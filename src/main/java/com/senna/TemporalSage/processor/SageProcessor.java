package com.senna.TemporalSage.processor;

// ---------------------- 추가된 import ----------------------

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedAnnotationDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.auto.service.AutoService;
import com.senna.TemporalSage.annotations.Determinism;
import com.senna.TemporalSage.annotations.SageService;
import com.senna.TemporalSage.annotations.Workflowable;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.springframework.stereotype.Component;

/**
 * Advanced Annotation Processor 예시:
 * 1) 제네릭/배열/중첩 클래스 시그니처 정교한 매칭
 * 2) @Determinism AST 분석 (SymbolSolver)
 * 3) Temporal/Cadence 결정적/비결정적 API 구분
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes({"com.senna.TemporalSage.annotations.SageService"})
public class SageProcessor extends AbstractProcessor {

  private Messager messager;
  private Filer filer;

  // 사용자로부터 받은 소스 경로 저장
  private final List<Path> sourcePaths = new ArrayList<>();

  // SymbolSolver 설정
  private CombinedTypeSolver combinedTypeSolver;

  // 예: SagaActivity<T, R> 의 "execute -> compensate"
  private static final Map<String, String> ACTIVITY_COMPENSATION_MAP = new HashMap<>();
  static {
    ACTIVITY_COMPENSATION_MAP.put("execute", "compensate");
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.messager = processingEnv.getMessager();
    this.filer = processingEnv.getFiler();

    // 1) sourcepath 옵션 파싱
    String sourcePathOption = processingEnv.getOptions().get("sourcepath");
    if (sourcePathOption != null) {
      String[] paths = sourcePathOption.split(":");
      for (String p : paths) {
        sourcePaths.add(Paths.get(p));
      }
    }

    // 2) SymbolSolver - CombinedTypeSolver 구성
    combinedTypeSolver = new CombinedTypeSolver();

    // (a) Reflection 기반 (JDK, 등)
    combinedTypeSolver.add(new ReflectionTypeSolver());

    // (b) 실제 프로젝트 소스 경로를 추가
    for (Path sp : sourcePaths) {
      if (Files.exists(sp)) {
        combinedTypeSolver.add(new JavaParserTypeSolver(sp.toFile()));
      }
    }
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // @SageService 붙은 클래스 스캔
    for (Element e : roundEnv.getElementsAnnotatedWith(SageService.class)) {
      if (!(e instanceof TypeElement)) continue;
      TypeElement sagaServiceType = (TypeElement) e;

      // (A) SagaActivity 필드 스캔
      List<VariableElement> sagaActivityFields = findSagaActivityFields(sagaServiceType);

      // (B) @Workflowable 메서드 스캔
      for (Element enclosed : sagaServiceType.getEnclosedElements()) {
        if (enclosed instanceof ExecutableElement) {
          ExecutableElement methodElement = (ExecutableElement) enclosed;
          Workflowable wf = methodElement.getAnnotation(Workflowable.class);
          if (wf != null) {
            // 1) 메서드 바디 + 결정성/보상 로직 분석
            ParsedMethodResult result = parseAndAnalyzeMethod(
                sagaServiceType, methodElement, sagaActivityFields
            );

            // 2) 워크플로 코드 생성
            generateWorkflowCode(sagaServiceType, methodElement, sagaActivityFields, result);
          }
        }
      }
    }
    return false;
  }

  // ---------------------------------------------------
  // (A) SagaActivity 필드 스캔
  // ---------------------------------------------------
  private List<VariableElement> findSagaActivityFields(TypeElement sagaServiceType) {
    List<VariableElement> fields = new ArrayList<>();
    for (Element enclosed : sagaServiceType.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.FIELD && enclosed instanceof VariableElement) {
        VariableElement ve = (VariableElement) enclosed;
        if (isSagaActivity(ve)) {
          fields.add(ve);
        }
      }
    }
    return fields;
  }

  private boolean isSagaActivity(VariableElement field) {
    // 단순 문자열 체크 예시: "SagaActivity"
    // 실제론 Types.isAssignable(...) 등을 사용하는 것이 안전
    return field.asType().toString().contains("SagaActivity");
  }

  // ---------------------------------------------------
  // (B) @Workflowable 메서드 분석: 제네릭/배열 시그니처 + 결정성 체크
  // ---------------------------------------------------
  private ParsedMethodResult parseAndAnalyzeMethod(
      TypeElement sagaServiceType,
      ExecutableElement methodElement,
      List<VariableElement> sagaActivityFields
  ) {
    ParsedMethodResult result = new ParsedMethodResult();
    String qName = sagaServiceType.getQualifiedName().toString();
    String methodName = methodElement.getSimpleName().toString();

    // 파라미터 정보 (제네릭/배열/중첩클래스 등)
    List<? extends VariableElement> paramElems = methodElement.getParameters();

    // 소스 경로에서 qName.java 찾기
    for (Path sp : sourcePaths) {
      Path candidate = sp.resolve(qName.replace('.', '/') + ".java");
      if (Files.exists(candidate)) {
        try {
          // JavaParser로 읽고 SymbolResolver 세팅
          com.github.javaparser.ast.CompilationUnit cu = StaticJavaParser.parse(candidate);
          SymbolResolver resolver = new JavaSymbolSolver(combinedTypeSolver);
          cu.setData(Node.SYMBOL_RESOLVER_KEY, resolver);

          // 클래스/인터페이스 선언 찾기
          List<ClassOrInterfaceDeclaration> cids = cu.findAll(ClassOrInterfaceDeclaration.class);
          for (ClassOrInterfaceDeclaration cid : cids) {
            if (!cid.getNameAsString().equals(sagaServiceType.getSimpleName().toString()))
              continue;

            // 메서드들 중 이름+시그니처 매칭
            for (MethodDeclaration md : cid.getMethods()) {
              if (!md.getNameAsString().equals(methodName)) continue;
              if (!signatureMatchesAdvanced(md, paramElems)) continue;

              // (2) 메서드 바디 분석
              md.getBody().ifPresent(body -> {
                result.originalBody = body.toString();
                List<MethodCallExpr> calls = body.findAll(MethodCallExpr.class);
                for (MethodCallExpr call : calls) {
                  analyzeCallWithSymbolSolver(call, sagaActivityFields, result);
                }
              });

              return result; // 찾았으면 반환
            }
          }

        } catch (FileNotFoundException e) {
          e.printStackTrace();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    return result;
  }

  /**
   * 고급 시그니처 매칭 (제네릭/배열/중첩클래스 등)
   */
  private boolean signatureMatchesAdvanced(MethodDeclaration md, List<? extends VariableElement> paramElems) {
    if (md.getParameters().size() != paramElems.size()) {
      return false;
    }
    // 파라미터 각각 비교
    for (int i = 0; i < paramElems.size(); i++) {
      TypeMirror paramMirror = paramElems.get(i).asType();
      com.github.javaparser.ast.body.Parameter astParam = md.getParameters().get(i);

      try {
        ResolvedType rt = astParam.getType().resolve();
        String apTypeStr = paramMirror.toString();
        String astTypeStr = rt.describe();

        if (!isSameTypeOrCompatible(apTypeStr, astTypeStr)) {
          return false;
        }

      } catch (Exception ex) {
        ex.printStackTrace();
        return false;
      }
    }
    return true;
  }

  /**
   * "java.util.List<java.lang.String>" vs "java.util.List<java.lang.String>"
   * "java.lang.String[]" vs "[Ljava.lang.String;"
   * "T" vs "java.lang.Object" 등등
   */
  private boolean isSameTypeOrCompatible(String fromAP, String fromAst) {
    if (fromAP.equals(fromAst)) {
      return true;
    }
    // 배열: "java.lang.String[]" vs "[Ljava.lang.String;"
    if (fromAP.replace("[]", "").equals(fromAst.replace("[]", ""))) {
      return true;
    }
    // 제네릭 T vs Object
    if (fromAP.equals("T") && fromAst.equals("java.lang.Object")) {
      return true;
    }
    return false;
  }

  // ---------------------------------------------------
  // (C) AST SymbolSolver로 결정성 & 보상 로직 체크
  // ---------------------------------------------------
  private void analyzeCallWithSymbolSolver(
      MethodCallExpr call,
      List<VariableElement> sagaActivityFields,
      ParsedMethodResult result
  ) {
    try {
      ResolvedMethodDeclaration rmd = call.resolve();
      String qName = rmd.getQualifiedName();

      // (a) @Determinism
      if (!isDeterministic(rmd)) {
        result.hasDeterminismError = true;
        result.determinismErrors.add("Non-deterministic call: " + qName);
      }

      // (b) Temporal/Cadence 구분
      if (isTemporalDeterministicMethod(rmd)) {
        // OK
      } else if (isSystemNonDeterministicMethod(rmd)) {
        result.hasDeterminismError = true;
        result.determinismErrors.add("System call is non-deterministic: " + qName);
      }

      // (c) SagaActivity.execute() → compensate
      detectActivityCall(call, sagaActivityFields, rmd, result);

    } catch (UnsolvedSymbolException ex) {
      result.hasDeterminismError = true;
      result.determinismErrors.add("Unresolved symbol: " + call.toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean isDeterministic(ResolvedMethodDeclaration rmd) {
    // 메서드 자체에 @Determinism?
    if (hasDeterminismAnnotation(rmd)) {
      return true;
    }
    // declaringType (클래스/인터페이스)에 @Determinism?
    ResolvedReferenceTypeDeclaration container = rmd.declaringType();
    if (hasDeterminismAnnotation(container)) {
      return true;
    }
    return false;
  }

  private boolean hasDeterminismAnnotation(ResolvedMethodDeclaration rmd) {
    JavaParserMethodDeclaration javaParserMethodDeclaration = (JavaParserMethodDeclaration) rmd;
    for (ResolvedAnnotationDeclaration ann : javaParserMethodDeclaration.declaringType()
        .getDeclaredAnnotations()) {
      if (ann.getQualifiedName().equals(Determinism.class.getCanonicalName())) {
        return true;
      }
    }
    return false;
  }

  private boolean hasDeterminismAnnotation(ResolvedReferenceTypeDeclaration typeDecl) {
    return typeDecl.hasAnnotation(Determinism.class.getCanonicalName());
  }

  private boolean isTemporalDeterministicMethod(ResolvedMethodDeclaration rmd) {
    String qName = rmd.getQualifiedName();
    // 예: "io.temporal.workflow.Workflow.currentTimeMillis"
    if (qName.startsWith("io.temporal.workflow.Workflow.")) {
      if (rmd.getName().equals("currentTimeMillis")) {
        return true;
      }
    }
    return false;
  }

  private boolean isSystemNonDeterministicMethod(ResolvedMethodDeclaration rmd) {
    String qName = rmd.getQualifiedName();
    if (qName.equals("java.lang.System.currentTimeMillis") ||
        qName.equals("java.lang.System.nanoTime")) {
      return true;
    }
    return false;
  }

  private void detectActivityCall(
      MethodCallExpr call,
      List<VariableElement> sagaActivityFields,
      ResolvedMethodDeclaration rmd,
      ParsedMethodResult result
  ) {
    if (ACTIVITY_COMPENSATION_MAP.containsKey(rmd.getName())) {
      call.getScope().ifPresent(scope -> {
        String scopeStr = scope.toString();
        for (VariableElement ve : sagaActivityFields) {
          if (scopeStr.endsWith(ve.getSimpleName().toString())) {
            result.needCompensationCalls.add(
                new CompensationCall(
                    ve.getSimpleName().toString(),
                    ACTIVITY_COMPENSATION_MAP.get(rmd.getName())
                )
            );
          }
        }
      });
    }
  }

  // ---------------------------------------------------
  // (D) 워크플로 코드 생성 (JavaPoet)
  // ---------------------------------------------------
  private void generateWorkflowCode(
      TypeElement sagaServiceClass,
      ExecutableElement workflowableMethod,
      List<VariableElement> sagaActivityFields,
      ParsedMethodResult parsedResult
  ) {
    if (parsedResult.hasDeterminismError) {
      for (String err : parsedResult.determinismErrors) {
        messager.printMessage(Diagnostic.Kind.ERROR,
            "[AdvancedSageProcessor] " + err);
      }
      // 실제로는 생성 중단
      return;
    }

    String methodName = workflowableMethod.getSimpleName().toString();
    String interfaceName = toUpperFirst(methodName) + "WorkflowInterface";
    String implName = toUpperFirst(methodName) + "WorkflowInterfaceImpl";

    TypeSpec workflowInterface = createWorkflowInterfaceSpec(interfaceName, workflowableMethod, methodName);
    TypeSpec workflowImpl = createWorkflowImplSpec(
        implName, interfaceName, workflowableMethod, methodName, sagaActivityFields, parsedResult
    );

    String pkgName = processingEnv.getElementUtils()
        .getPackageOf(sagaServiceClass)
        .getQualifiedName().toString();

    try {
      JavaFile.builder(pkgName, workflowInterface).build().writeTo(filer);
      JavaFile.builder(pkgName, workflowImpl).build().writeTo(filer);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private TypeSpec createWorkflowInterfaceSpec(
      String interfaceName,
      ExecutableElement methodElement,
      String methodName
  ) {
    return TypeSpec.interfaceBuilder(interfaceName)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(WorkflowInterface.class)
        .addMethod(createWorkflowMethodSpec(methodElement, methodName))
        .build();
  }

  private MethodSpec createWorkflowMethodSpec(ExecutableElement methodElement, String methodName) {
    MethodSpec.Builder b = MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        .addAnnotation(WorkflowMethod.class)
        .returns(TypeName.get(methodElement.getReturnType()));

    for (VariableElement ve : methodElement.getParameters()) {
      b.addParameter(
          TypeName.get(ve.asType()),
          ve.getSimpleName().toString(),
          Modifier.FINAL
      );
    }
    return b.build();
  }

  private TypeSpec createWorkflowImplSpec(
      String implName,
      String interfaceName,
      ExecutableElement methodElement,
      String methodName,
      List<VariableElement> sagaActivityFields,
      ParsedMethodResult parsedResult
  ) {
    TypeSpec.Builder implBuilder = TypeSpec.classBuilder(implName)
        .addModifiers(Modifier.PUBLIC)
        .addSuperinterface(ClassName.get("", interfaceName))
        .addAnnotation(Component.class);

    // SagaActivity 필드
    for (VariableElement field : sagaActivityFields) {
      TypeName fieldType = TypeName.get(field.asType());
      FieldSpec fieldSpec = FieldSpec.builder(
          fieldType, field.getSimpleName().toString(), Modifier.PRIVATE
      ).build();
      implBuilder.addField(fieldSpec);
    }

    // 생성자
    implBuilder.addMethod(createConstructor(sagaActivityFields));

    // 워크플로 메서드 구현
    implBuilder.addMethod(createWorkflowMethodImpl(
        methodElement, methodName, parsedResult
    ));

    return implBuilder.build();
  }

  private MethodSpec createConstructor(List<VariableElement> sagaActivityFields) {
    MethodSpec.Builder ctor = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PUBLIC);

    for (VariableElement field : sagaActivityFields) {
      String fieldName = field.getSimpleName().toString();
      ctor.addStatement("$T $NOptions = null", ActivityOptions.class, fieldName);

      ctor.addStatement(
          "$NOptions = $T.newBuilder().build()",
          fieldName, ActivityOptions.class
      );

      // stub 생성
      ctor.addStatement(
          "this.$N = $T.newActivityStub($T.class, $NOptions)",
          fieldName,
          Workflow.class,
          TypeName.get(field.asType()),
          fieldName
      );
    }
    return ctor.build();
  }

  private MethodSpec createWorkflowMethodImpl(
      ExecutableElement methodElement,
      String methodName,
      ParsedMethodResult parsedResult
  ) {
    TypeName returnType = TypeName.get(methodElement.getReturnType());
    MethodSpec.Builder mb = MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC)
        .returns(returnType);

    for (VariableElement ve : methodElement.getParameters()) {
      mb.addParameter(
          TypeName.get(ve.asType()),
          ve.getSimpleName().toString(),
          Modifier.FINAL
      );
    }

    mb.addStatement("$T saga = new $T()", Saga.class, Saga.class);

    CodeBlock.Builder block = CodeBlock.builder()
        .addStatement("try {")
        .addStatement("  // Original Method Body Start");

    String body = parsedResult.originalBody;
    block.addStatement("  $L", formatBodyForCodeBlock(body));
    block.addStatement("  // Original Method Body End");

    for (CompensationCall cc : parsedResult.needCompensationCalls) {
      block.addStatement(
          "  saga.addCompensation(() -> { this.$N.$N(null); })",
          cc.fieldName, cc.compensationMethod
      );
    }

    block
        .addStatement("} catch (Exception e) {")
        .addStatement("  saga.compensate();")
        .addStatement("  throw new RuntimeException(e);")
        .addStatement("}");

    mb.addCode(block.build());

    if (!returnType.toString().equals("void")) {
      mb.addStatement("return null // TODO: proper return");
    }
    return mb.build();
  }

  private String toUpperFirst(String s) {
    if (s == null || s.isEmpty()) return s;
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  private String formatBodyForCodeBlock(String body) {
    if (body == null) return "";
    String trimmed = body.trim();
    if (trimmed.startsWith("{")) {
      trimmed = trimmed.substring(1);
    }
    if (trimmed.endsWith("}")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed.trim();
  }

  // ---------------------------------------------------
  // DTO
  // ---------------------------------------------------
  private static class ParsedMethodResult {
    String originalBody = "";
    boolean hasDeterminismError = false;
    List<String> determinismErrors = new ArrayList<>();
    List<CompensationCall> needCompensationCalls = new ArrayList<>();
  }

  private static class CompensationCall {
    final String fieldName;
    final String compensationMethod;

    CompensationCall(String fieldName, String compensationMethod) {
      this.fieldName = fieldName;
      this.compensationMethod = compensationMethod;
    }
  }
}