package com.senna.TemporalSage.processor;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.auto.service.AutoService;
import com.senna.TemporalSage.annotations.Determinism;
import com.senna.TemporalSage.annotations.GeneratedWorkflow;
import com.senna.TemporalSage.annotations.Option;
import com.senna.TemporalSage.annotations.SageService;
import com.senna.TemporalSage.annotations.Workflowable;
import com.senna.TemporalSage.saga.SagaActivity;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
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
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 수정된 SageProcessor 예시
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes({"com.senna.TemporalSage.annotations.SageService"})
public class SageProcessor extends AbstractProcessor {

  private Messager messager;
  private Filer filer;

  private final List<Path> sourcePaths = new ArrayList<>();
  private final List<Path> classPaths = new ArrayList<>();
  private CombinedTypeSolver combinedTypeSolver;

  private int variableCount = 0;

  private ProcessingEnvironment processingEnv;

  // "execute" -> "compensate"
  private static final Map<String, String> ACTIVITY_COMPENSATION_MAP = new HashMap<>();

  static {
    ACTIVITY_COMPENSATION_MAP.put("execute", "compensate");
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.messager = processingEnv.getMessager();
    this.filer = processingEnv.getFiler();
    this.processingEnv = processingEnv;

    String sourcePathOption = processingEnv.getOptions().get("sourcepath");
    if (sourcePathOption != null) {
      String[] paths = sourcePathOption.split(":");
      for (String p : paths) {
        sourcePaths.add(Paths.get(p));
      }
    }

    String classPathOption = processingEnv.getOptions().get("classpath");
    if (classPathOption != null) {
      String[] paths = classPathOption.split(":");
      for (String p : paths) {
        classPaths.add(Paths.get(p));
      }
    }

    combinedTypeSolver = new CombinedTypeSolver();
    combinedTypeSolver.add(new ReflectionTypeSolver());
    for (Path sp : sourcePaths) {
      if (Files.exists(sp)) {
        combinedTypeSolver.add(new JavaParserTypeSolver(sp.toFile()));
      }
    }

    try {
      for (Path cp : classPaths) {
        if (Files.exists(cp)) {
          combinedTypeSolver.add(new JarTypeSolver(cp.toFile()));
        }
      }
    } catch (IOException e) {
      messager.printMessage(Kind.ERROR, "Failed to add classpath" + e.getMessage());
      e.printStackTrace();
    }
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> sagaServiceClasses = roundEnv.getElementsAnnotatedWith(
        SageService.class);

    for (Element e : sagaServiceClasses) {
      if (!(e instanceof TypeElement)) {
        continue;
      }
      TypeElement sagaServiceType = (TypeElement) e;

      List<VariableElement> sagaActivityFields = findSagaActivityFields(sagaServiceType);

      if (sagaActivityFields.isEmpty()) {
        messager.printMessage(Diagnostic.Kind.ERROR,
            "SagaActivity fields are not found, Is it workflow?", e);
      }

      for (Element enclosed : sagaServiceType.getEnclosedElements()) {
        if (enclosed instanceof ExecutableElement) {
          ExecutableElement methodElement = (ExecutableElement) enclosed;
          Workflowable wf = methodElement.getAnnotation(Workflowable.class);
          if (wf != null) {
            ParsedMethodResult result =
                parseAndAnalyzeMethod(sagaServiceType, methodElement, sagaActivityFields);

            this.generateWorkflowCode(sagaServiceType, methodElement, sagaActivityFields, result);
          }
        }
      }
    }
    return false;
  }

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
    TypeMirror type = field.asType();
    if (type.getKind() == TypeKind.DECLARED) {
      DeclaredType declaredType = (DeclaredType) type;
      Element element = declaredType.asElement();

      if (element instanceof TypeElement) {
        TypeElement typeElement = (TypeElement) element;

        List<? extends TypeMirror> interfaces = typeElement.getInterfaces();

        for (TypeMirror iface : interfaces) {
          Element ifaceElement = ((DeclaredType) iface).asElement();
          if (ifaceElement instanceof TypeElement) {
            TypeElement ifaceTypeElement = (TypeElement) ifaceElement;
            messager.printMessage(Diagnostic.Kind.NOTE,
                "Interface: " + ifaceTypeElement.getSimpleName());
            boolean isSagaActivityInterface = ifaceTypeElement.getSimpleName().toString()
                .equals("SagaActivity");

            List<? extends AnnotationMirror> annotationMirrors = ifaceTypeElement.getAnnotationMirrors();

            boolean hasActivityAnnotation = annotationMirrors.stream().anyMatch(mirror -> {
              Element annotationTypeElement = mirror.getAnnotationType().asElement();
              if (annotationTypeElement instanceof TypeElement) {
                TypeElement annoTypeElem = (TypeElement) annotationTypeElement;
                messager.printMessage(Diagnostic.Kind.NOTE,
                    "Annotation: " + annoTypeElem.getQualifiedName());
                return annoTypeElem.getQualifiedName()
                    .contentEquals("io.temporal.activity.ActivityInterface");
              }
              return false;
            });

            return isSagaActivityInterface && hasActivityAnnotation;
          }
        }
      }
    }
    return false;
  }

  private ParsedMethodResult parseAndAnalyzeMethod(
      TypeElement sagaServiceType,
      ExecutableElement methodElement,
      List<VariableElement> sagaActivityFields
  ) {
    ParsedMethodResult result = new ParsedMethodResult();
    String qName = sagaServiceType.getQualifiedName().toString();
    String methodName = methodElement.getSimpleName().toString();
    List<? extends VariableElement> paramElems = methodElement.getParameters();

    for (Path sp : sourcePaths) {
      Path candidate = sp.resolve(qName.replace('.', '/') + ".java");
      if (Files.exists(candidate)) {
        try {
          com.github.javaparser.ast.CompilationUnit cu = StaticJavaParser.parse(candidate);
          SymbolResolver resolver = new JavaSymbolSolver(combinedTypeSolver);
          cu.setData(Node.SYMBOL_RESOLVER_KEY, resolver);

          List<ClassOrInterfaceDeclaration> cids = cu.findAll(ClassOrInterfaceDeclaration.class);
          for (ClassOrInterfaceDeclaration cid : cids) {
            if (!cid.getNameAsString().equals(sagaServiceType.getSimpleName().toString())) {
              continue;
            }
            for (MethodDeclaration md : cid.getMethods()) {
              if (!md.getNameAsString().equals(methodName)) {
                continue;
              }
              if (!signatureMatchesAdvanced(md, paramElems)) {
                continue;
              }

              md.getBody().ifPresent(body -> {
                result.originalBody = body.toString();
                List<MethodCallExpr> calls = body.findAll(MethodCallExpr.class);
                for (MethodCallExpr call : calls) {
                  analyzeCallWithSymbolSolver(call, sagaActivityFields, result);
                }
              });
              return result;
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    return result;
  }

  private boolean signatureMatchesAdvanced(MethodDeclaration md,
      List<? extends VariableElement> paramElems) {
    if (md.getParameters().size() != paramElems.size()) {
      return false;
    }
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

  private boolean isSameTypeOrCompatible(String fromAP, String fromAst) {
    if (fromAP.equals(fromAst)) {
      return true;
    }
    if (fromAP.replace("[]", "").equals(fromAst.replace("[]", ""))) {
      return true;
    }
    if (fromAP.equals("T") && fromAst.equals("java.lang.Object")) {
      return true;
    }
    return false;
  }

  private void analyzeCallWithSymbolSolver(
      MethodCallExpr call,
      List<VariableElement> sagaActivityFields,
      ParsedMethodResult result
  ) {
    try {
      messager.printMessage(Diagnostic.Kind.NOTE, "analyzeCall: " + call.toString());
      ResolvedMethodDeclaration rmd;
      try {
        rmd = call.resolve();
      } catch (UnsolvedSymbolException unsolvedSymbolException) {
        messager.printMessage(Kind.WARNING, "Unresolved symbol: %s".formatted(call.toString()));
        return;
      }
      String qName = rmd.getQualifiedName();

      if (!detectActivityCall(call, sagaActivityFields, rmd, result)) {
        // 결정성 체크
        if (!isDeterministic(rmd)) {
          result.hasDeterminismError = true;
          result.determinismErrors.add("Non-deterministic call: " + qName);
        }
      }

    } catch (UnsolvedSymbolException ex) {
      result.hasDeterminismError = true;
      result.determinismErrors.add("Unresolved symbol: " + call.toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean isDeterministic(ResolvedMethodDeclaration rmd) {
    if (hasDeterminismAnnotation(rmd)) {
      return true;
    }
    ResolvedReferenceTypeDeclaration container = rmd.declaringType();
    return hasDeterminismAnnotation(container);
  }

  private boolean hasDeterminismAnnotation(ResolvedMethodDeclaration rmd) {
    if (rmd instanceof JavaParserMethodDeclaration) {
      JavaParserMethodDeclaration jpm = (JavaParserMethodDeclaration) rmd;
      for (AnnotationExpr ann : jpm.getWrappedNode().getAnnotations()) {
        String qName = ann.getName().toString();
        if (Determinism.class.getCanonicalName().equals(qName)) {
          return true;
        }
      }
      if (hasDeterminismAnnotation(jpm.declaringType())) {
        return true;
      }
    }
    return false;
  }

  private boolean hasDeterminismAnnotation(ResolvedReferenceTypeDeclaration typeDecl) {
    return typeDecl.hasAnnotation(Determinism.class.getCanonicalName());
  }

  private boolean detectActivityCall(
      MethodCallExpr call,
      List<VariableElement> sagaActivityFields,
      ResolvedMethodDeclaration rmd,
      ParsedMethodResult result
  ) {
    StringBuilder sb = new StringBuilder();
    if (!rmd.getReturnType().isVoid()) {
      sb.append(rmd.getReturnType().describe()).append(" ").append(this.getVariableName())
          .append(" = ");
    }

    sb.append(call.toString());

    String realStatement = sb.toString();

    messager.printMessage(Diagnostic.Kind.NOTE, "Call: " + call.toString());
    messager.printMessage(Diagnostic.Kind.NOTE, "Method: " + rmd.getName());
    List<String> arguments = call.getArguments().stream().map(arg -> arg.toString())
        .toList();
    rmd.getTypeParameters().forEach(tp -> messager.printMessage(Diagnostic.Kind.NOTE, "TypeParam: " + tp));
    if (ACTIVITY_COMPENSATION_MAP.containsKey(rmd.getName())) {
      call.getScope().ifPresent(scope -> {
        String scopeStr = scope.toString();
        for (VariableElement ve : sagaActivityFields) {
          if (scopeStr.endsWith(ve.getSimpleName().toString())) {
            // [변경점] 보상 로직 등록
            result.needCompensationCalls.add(
                new CompensationCall(
                    ve.getSimpleName().toString(),
                    ACTIVITY_COMPENSATION_MAP.get(rmd.getName()),
                    scopeStr,
                    realStatement,
                    arguments
                )
            );
          }
        }
      });
      return true;
    }
    return false;
  }

  // ---------------------------------------------------
  // (D) 코드 생성
  // ---------------------------------------------------
  private void generateWorkflowCode(
      TypeElement sagaServiceClass,
      ExecutableElement workflowableMethod,
      List<VariableElement> sagaActivityFields,
      ParsedMethodResult parsedResult
  ) {
    if (parsedResult.hasDeterminismError) {
      for (String err : parsedResult.determinismErrors) {
        messager.printMessage(Diagnostic.Kind.ERROR, "[SageProcessor] " + err);
      }
//      return;
    }

    String methodName = workflowableMethod.getSimpleName().toString();
    String interfaceName = toUpperFirst(methodName) + "WorkflowInterface";
    String implName = toUpperFirst(methodName) + "WorkflowInterfaceImpl";

    TypeSpec workflowInterface = createWorkflowInterfaceSpec(interfaceName, workflowableMethod,
        methodName);
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
      b.addParameter(TypeName.get(ve.asType()), ve.getSimpleName().toString(), Modifier.FINAL);
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
        .addAnnotation(Component.class)
        .addAnnotation(GeneratedWorkflow.class);

    for (VariableElement field : sagaActivityFields) {
      TypeName fieldType = TypeName.get(field.asType());
      TypeElement typeElement = this.processingEnv.getElementUtils()
          .getTypeElement(fieldType.toString());

      TypeName sagaActivityInterfaceTypeName = ClassName.get(SagaActivity.class);
      for (TypeMirror tm : typeElement.getInterfaces()) {
        if (tm instanceof DeclaredType) {
          DeclaredType declared = (DeclaredType) tm;
          // 제네릭 파라미터
          List<? extends TypeMirror> typeArgs = declared.getTypeArguments();
          if (typeArgs.size() == 2) {
            ClassName className = ClassName.get(SagaActivity.class);
            TypeName genericParam = ClassName.get(typeArgs.get(0));
            TypeName geneticReturn = ClassName.get(typeArgs.get(1));
            sagaActivityInterfaceTypeName = ParameterizedTypeName.get(className, genericParam, geneticReturn);
          }
        }
      }

      FieldSpec fieldSpec = FieldSpec.builder(sagaActivityInterfaceTypeName, field.getSimpleName().toString())
          .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
          .build();
      implBuilder.addField(fieldSpec);
    }

    implBuilder.addMethod(createConstructor(sagaActivityFields));
    implBuilder.addMethod(createDefaultConstructor(sagaActivityFields));

    implBuilder.addMethod(createWorkflowMethodImpl(methodElement, methodName, parsedResult));

    return implBuilder.build();
  }

  private MethodSpec createDefaultConstructor(List<VariableElement> sagaActivityFields) {
    MethodSpec.Builder ctor = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Autowired.class);

    for (VariableElement field : sagaActivityFields) {
      TypeName fieldType = TypeName.get(field.asType());
      String fieldName = field.getSimpleName().toString();

      // 파라미터 추가
      ctor.addParameter(fieldType, fieldName, Modifier.FINAL);
      // 필드에 할당
      ctor.addStatement("this.$N = $N", fieldName, fieldName);
    }

    return ctor.build();
  }

  private MethodSpec createConstructor(List<VariableElement> sagaActivityFields) {
    MethodSpec.Builder ctor = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PUBLIC);

    for (VariableElement sagaActivityField : sagaActivityFields) {
      Option options = sagaActivityField.asType().getAnnotation(Option.class);

      TypeName fieldType = TypeName.get(sagaActivityField.asType());
      String fieldName = sagaActivityField.getSimpleName().toString();

      TypeElement typeElement = this.processingEnv.getElementUtils()
          .getTypeElement(fieldType.toString());
      String taskQueue = ((ClassName) fieldType).simpleName();

      ctor.addCode(OptionUtils.createActivityOptions(options, taskQueue));
      for (TypeMirror tm : typeElement.getInterfaces()) {
        if (tm instanceof DeclaredType) {
          DeclaredType declared = (DeclaredType) tm;
          // 제네릭 파라미터
          List<? extends TypeMirror> typeArgs = declared.getTypeArguments();
          if (typeArgs.size() == 2) {
            TypeName genericParam = ClassName.get(typeArgs.get(0));
            TypeName geneticReturn = ClassName.get(typeArgs.get(1));
            ctor.addStatement("this.$N = ($T<$T, $T>)$T.newActivityStub($T.class, $N)", fieldName, SagaActivity.class, genericParam, geneticReturn, Workflow.class,
                SagaActivity.class, taskQueue);
          }
        }
      }
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

    // 메서드 파라미터
    for (VariableElement ve : methodElement.getParameters()) {
      mb.addParameter(TypeName.get(ve.asType()), ve.getSimpleName().toString(), Modifier.FINAL);
    }

    mb.addStatement("$T saga = new $T(new $T.Builder().build())",
        Saga.class, Saga.class, Saga.Options.class);

    CodeBlock.Builder block = CodeBlock.builder()
        .addStatement("try {");

    for (CompensationCall cc : parsedResult.needCompensationCalls) {
      block.addStatement(cc.realStatement);
      String args = String.join(", ", cc.arguments);
      block.addStatement("  " + "saga.addCompensation(() -> " + cc.scope + ".compensate($N))", args);
    }

    // catch
    block
        .addStatement("} catch (Exception e) {")
        .addStatement("  saga.compensate()")
        .addStatement("  throw new RuntimeException(e)")
        .addStatement("}");

    mb.addCode(block.build());

    // return
    if (!returnType.toString().equals("void")) {
      mb.addStatement("return null");
    }
    return mb.build();
  }

  private String toUpperFirst(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  private String getVariableName() {
    return "var" + this.variableCount++;
  }

  // DTO
  private static class ParsedMethodResult {

    String originalBody = "";
    boolean hasDeterminismError = false;
    List<String> determinismErrors = new ArrayList<>();
    List<CompensationCall> needCompensationCalls = new ArrayList<>();
  }

  private static class CompensationCall {

    final String fieldName;
    final String compensationMethod;
    final String scope;
    final String realStatement;
    final List<String> arguments;

    CompensationCall(String fieldName, String compensationMethod, String scope, String realStatement, List<String> args) {
      this.fieldName = fieldName;
      this.compensationMethod = compensationMethod;
      this.scope = scope;
      this.realStatement = realStatement;
      this.arguments = args;
    }
  }
}