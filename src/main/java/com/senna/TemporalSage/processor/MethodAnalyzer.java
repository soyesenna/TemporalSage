package com.senna.TemporalSage.processor;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
import com.senna.TemporalSage.annotations.Determinism;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

public class MethodAnalyzer {

  private MethodAnalyzer() {
    // util 클래스 - 인스턴스화 방지
  }

  /**
   * 메서드를 찾아서 본문을 파싱하고, 액티비티 호출을 분석한다.
   */
  public static ParsedMethodResult parseMethodAndAnalyzeActivityCalls(
      TypeElement sagaServiceType,
      ExecutableElement methodElement,
      List<VariableElement> sagaActivityFields,
      List<Path> sourcePaths,
      CombinedTypeSolver combinedTypeSolver,
      Messager messager,
      VariableNameGenerator variableNameGenerator,  // "var0", "var1" 생성기
      Map<String, String> activityCompensationMap
  ) {
    ParsedMethodResult result = new ParsedMethodResult();

    String qName = sagaServiceType.getQualifiedName().toString();
    String methodName = methodElement.getSimpleName().toString();
    List<? extends VariableElement> paramElems = methodElement.getParameters();

    // 소스 경로 순회하며 .java 파일 찾기
    for (Path sp : sourcePaths) {
      Path candidate = sp.resolve(qName.replace('.', '/') + ".java");
      if (Files.exists(candidate)) {
        try {
          var cu = StaticJavaParser.parse(candidate);
          SymbolResolver resolver = new JavaSymbolSolver(combinedTypeSolver);
          cu.setData(Node.SYMBOL_RESOLVER_KEY, resolver);

          // 클래스/인터페이스 선언 찾아서 일치하는 메서드 찾기
          List<ClassOrInterfaceDeclaration> cids = cu.findAll(ClassOrInterfaceDeclaration.class);
          for (ClassOrInterfaceDeclaration cid : cids) {
            if (!cid.getNameAsString().equals(sagaServiceType.getSimpleName().toString())) {
              continue;
            }
            for (MethodDeclaration md : cid.getMethods()) {
              if (!md.getNameAsString().equals(methodName)) {
                continue;
              }
              // 시그니처(파라미터) 일치 여부
              if (!signatureMatchesMethodParameters(md, paramElems, messager)) {
                continue;
              }

              md.getBody().ifPresent(body -> {
                result.originalBody = body.toString();
                List<MethodCallExpr> calls = body.findAll(MethodCallExpr.class);
                for (MethodCallExpr call : calls) {
                  analyzeMethodCall(
                      call,
                      sagaActivityFields,
                      result,
                      messager,
                      resolver,
                      variableNameGenerator,
                      activityCompensationMap
                  );
                }
              });
              return result; // 메서드 찾으면 종료
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    return result;
  }

  /**
   * 메서드 호출을 분석하여 결정성 체크와 액티비티 보상 로직 등록 여부를 처리한다.
   */
  private static void analyzeMethodCall(
      MethodCallExpr call,
      List<VariableElement> sagaActivityFields,
      ParsedMethodResult result,
      Messager messager,
      SymbolResolver resolver,
      VariableNameGenerator variableNameGenerator,
      Map<String, String> activityCompensationMap
  ) {
    try {
      messager.printMessage(Diagnostic.Kind.NOTE, "analyzeCall: " + call.toString());
      ResolvedMethodDeclaration rmd;
      try {
        rmd = call.resolve();
      } catch (UnsolvedSymbolException unsolvedSymbolException) {
        messager.printMessage(Diagnostic.Kind.WARNING,
            "Unresolved symbol: " + call.toString());
        return;
      }
      String qName = rmd.getQualifiedName();

      // 액티비티 호출인지 확인하고, 맞다면 보상 로직에 등록
      boolean isActivity = detectAndRegisterActivityCall(
          call, sagaActivityFields, rmd, result,
          messager, variableNameGenerator, activityCompensationMap
      );
      // 액티비티가 아니면 결정성(Determinism) 체크
      if (!isActivity) {
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

  /**
   * 액티비티 호출인지 확인하고, 맞다면 보상 로직(CompensationCall)을 등록한다.
   * @return 액티비티 호출인지 여부
   */
  private static boolean detectAndRegisterActivityCall(
      MethodCallExpr call,
      List<VariableElement> sagaActivityFields,
      ResolvedMethodDeclaration rmd,
      ParsedMethodResult result,
      Messager messager,
      VariableNameGenerator variableNameGenerator,
      Map<String, String> activityCompensationMap
  ) {
    // 반환 타입이 void가 아니라면 varX = ... 식으로 문장화
    StringBuilder statementBuilder = new StringBuilder();
    if (!rmd.getReturnType().isVoid()) {
      statementBuilder.append(rmd.getReturnType().describe())
          .append(" ")
          .append(variableNameGenerator.getNextVariableName())
          .append(" = ");
    }
    statementBuilder.append(call.toString());

    String realStatement = statementBuilder.toString();
    messager.printMessage(Diagnostic.Kind.NOTE, "Call: " + call.toString());
    messager.printMessage(Diagnostic.Kind.NOTE, "Method: " + rmd.getName());

    List<String> arguments = call.getArguments().stream().map(Object::toString).toList();

    // 보상 메서드가 존재한다면(Call 이름이 execute -> compensate 로 매핑)
    if (activityCompensationMap.containsKey(rmd.getName())) {
      call.getScope().ifPresent(scope -> {
        String scopeStr = scope.toString();
        for (VariableElement ve : sagaActivityFields) {
          if (scopeStr.endsWith(ve.getSimpleName().toString())) {
            // 보상 로직 등록
            result.needCompensationCalls.add(
                new CompensationCall(
                    ve.getSimpleName().toString(),
                    activityCompensationMap.get(rmd.getName()),
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

  /**
   * 해당 메서드 선언과 실제 @Workflowable 메서드 파라미터 시그니처가 일치하는지 검사한다.
   */
  private static boolean signatureMatchesMethodParameters(
      MethodDeclaration md,
      List<? extends VariableElement> paramElems,
      Messager messager
  ) {
    if (md.getParameters().size() != paramElems.size()) {
      return false;
    }
    for (int i = 0; i < paramElems.size(); i++) {
      TypeMirror paramMirror = paramElems.get(i).asType();
      var astParam = md.getParameters().get(i);
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
   * 파라미터 타입 호환성 체크 (원래 로직 유지)
   */
  private static boolean isSameTypeOrCompatible(String fromAP, String fromAst) {
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

  /**
   * 메서드가 결정성(Determinism) 주석이 있는지 혹은 클래스가 Determinism인지 검사
   */
  private static boolean isDeterministic(ResolvedMethodDeclaration rmd) {
    if (hasDeterminismAnnotation(rmd)) {
      return true;
    }
    ResolvedReferenceTypeDeclaration container = rmd.declaringType();
    return hasDeterminismAnnotation(container);
  }

  /**
   * @Determinism 존재 유무
   */
  private static boolean hasDeterminismAnnotation(ResolvedMethodDeclaration rmd) {
    if (rmd instanceof JavaParserMethodDeclaration jpm) {
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

  /**
   * @Determinism 존재 유무
   */
  private static boolean hasDeterminismAnnotation(ResolvedReferenceTypeDeclaration typeDecl) {
    return typeDecl.hasAnnotation(Determinism.class.getCanonicalName());
  }

  // DTO (원래 구조와 로직 그대로 유지)
  public static class ParsedMethodResult {
    public String originalBody = "";
    public boolean hasDeterminismError = false;
    public List<String> determinismErrors = new ArrayList<>();
    public List<CompensationCall> needCompensationCalls = new ArrayList<>();
  }

  public static class CompensationCall {
    public final String fieldName;
    public final String compensationMethod;
    public final String scope;
    public final String realStatement;
    public final List<String> arguments;

    public CompensationCall(String fieldName, String compensationMethod, String scope,
        String realStatement, List<String> args) {
      this.fieldName = fieldName;
      this.compensationMethod = compensationMethod;
      this.scope = scope;
      this.realStatement = realStatement;
      this.arguments = args;
    }
  }

  /**
   * variableNameGenerator를 위한 간단한 Functional Interface.
   */
  @FunctionalInterface
  public interface VariableNameGenerator {
    String getNextVariableName();
  }
}