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
      VariableNameGenerator variableNameGenerator,
      Map<String, String> activityCompensationMap
  ) {
    ParsedMethodResult result = new ParsedMethodResult();

    String qName = sagaServiceType.getQualifiedName().toString();
    String methodName = methodElement.getSimpleName().toString();
    List<? extends VariableElement> paramElems = methodElement.getParameters();

    for (Path sp : sourcePaths) {
      Path candidate = sp.resolve(qName.replace('.', '/') + ".java");
      if (Files.exists(candidate)) {
        try {
          var cu = StaticJavaParser.parse(candidate);
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

      boolean isActivity = detectAndRegisterActivityCall(
          call, sagaActivityFields, rmd, result,
          messager, variableNameGenerator, activityCompensationMap
      );
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

  private static boolean detectAndRegisterActivityCall(
      MethodCallExpr call,
      List<VariableElement> sagaActivityFields,
      ResolvedMethodDeclaration rmd,
      ParsedMethodResult result,
      Messager messager,
      VariableNameGenerator variableNameGenerator,
      Map<String, String> activityCompensationMap
  ) {
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

  private static boolean isDeterministic(ResolvedMethodDeclaration rmd) {
    if (hasDeterminismAnnotation(rmd)) {
      return true;
    }
    ResolvedReferenceTypeDeclaration container = rmd.declaringType();
    return hasDeterminismAnnotation(container);
  }

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

  private static boolean hasDeterminismAnnotation(ResolvedReferenceTypeDeclaration typeDecl) {
    return typeDecl.hasAnnotation(Determinism.class.getCanonicalName());
  }

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

  @FunctionalInterface
  public interface VariableNameGenerator {
    String getNextVariableName();
  }
}