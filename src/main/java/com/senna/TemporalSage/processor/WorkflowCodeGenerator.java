package com.senna.TemporalSage.processor;

import com.senna.TemporalSage.annotations.GeneratedWorkflow;
import com.senna.TemporalSage.annotations.Option;
import com.squareup.javapoet.*;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.List;

public class WorkflowCodeGenerator {

  private WorkflowCodeGenerator() {
  }

  /**
   * 워크플로우 인터페이스 + 구현체를 생성하여 Filer에 기록한다.
   */
  public static void generateWorkflowCode(
      TypeElement sagaServiceClass,
      ExecutableElement workflowableMethod,
      List<VariableElement> sagaActivityFields,
      MethodAnalyzer.ParsedMethodResult parsedResult,
      Messager messager,
      Filer filer,
      javax.annotation.processing.ProcessingEnvironment processingEnv
  ) {
    // 결정성 에러가 있으면 메시지 출력 (원래 로직 그대로)
    if (parsedResult.hasDeterminismError) {
      for (String err : parsedResult.determinismErrors) {
        messager.printMessage(Diagnostic.Kind.ERROR, "[SageProcessor] " + err);
      }
      // 여기서 중단하지 않는 것도 원래 로직 그대로
    }

    String methodName = workflowableMethod.getSimpleName().toString();
    String interfaceName = toUpperFirst(methodName) + "WorkflowInterface";
    String implName = toUpperFirst(methodName) + "WorkflowInterfaceImpl";

    TypeSpec workflowInterface = buildWorkflowInterfaceSpec(interfaceName, workflowableMethod, methodName);
    TypeSpec workflowImpl = buildWorkflowImplSpec(
        implName,
        interfaceName,
        workflowableMethod,
        methodName,
        sagaActivityFields,
        parsedResult,
        processingEnv
    );

    String pkgName = processingEnv.getElementUtils()
        .getPackageOf(sagaServiceClass)
        .getQualifiedName().toString();

    try {
      JavaFile.builder(pkgName, workflowInterface)
          .build()
          .writeTo(filer);

      JavaFile.builder(pkgName, workflowImpl)
          .build()
          .writeTo(filer);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * 워크플로우 인터페이스 스펙 생성
   */
  private static TypeSpec buildWorkflowInterfaceSpec(
      String interfaceName,
      ExecutableElement methodElement,
      String methodName
  ) {
    return TypeSpec.interfaceBuilder(interfaceName)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(WorkflowInterface.class)
        .addMethod(buildWorkflowMethodSpec(methodElement, methodName))
        .build();
  }

  /**
   * @WorkflowMethod 붙은 메서드 스펙 생성
   */
  private static MethodSpec buildWorkflowMethodSpec(ExecutableElement methodElement, String methodName) {
    MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        .addAnnotation(WorkflowMethod.class)
        .returns(TypeName.get(methodElement.getReturnType()));

    // 파라미터 추가
    for (VariableElement ve : methodElement.getParameters()) {
      builder.addParameter(TypeName.get(ve.asType()), ve.getSimpleName().toString(), Modifier.FINAL);
    }
    return builder.build();
  }

  /**
   * 워크플로우 구현체 스펙 생성
   */
  private static TypeSpec buildWorkflowImplSpec(
      String implName,
      String interfaceName,
      ExecutableElement methodElement,
      String methodName,
      List<VariableElement> sagaActivityFields,
      MethodAnalyzer.ParsedMethodResult parsedResult,
      javax.annotation.processing.ProcessingEnvironment processingEnv
  ) {
    TypeSpec.Builder implBuilder = TypeSpec.classBuilder(implName)
        .addModifiers(Modifier.PUBLIC)
        .addSuperinterface(ClassName.get("", interfaceName))
        .addAnnotation(Component.class)
        .addAnnotation(GeneratedWorkflow.class);

    // SagaActivity 필드를 선언 (주어진 로직)
    for (VariableElement field : sagaActivityFields) {
      implBuilder.addField(buildSagaActivityFieldSpec(field, processingEnv));
    }

    // 생성자 2개 추가 (원래 로직)
    implBuilder.addMethod(buildAutowiredConstructor(sagaActivityFields));
    implBuilder.addMethod(buildActivityStubConstructor(sagaActivityFields, processingEnv));

    // 워크플로우 메서드 구현부
    implBuilder.addMethod(buildWorkflowMethodImplementation(methodElement, methodName, parsedResult));

    return implBuilder.build();
  }

  /**
   * SagaActivity 인터페이스 필드를 생성하는 FieldSpec
   */
  private static FieldSpec buildSagaActivityFieldSpec(VariableElement field, javax.annotation.processing.ProcessingEnvironment processingEnv) {
    TypeName fieldType = TypeName.get(field.asType());

    // SagaActivity<T,R> 인지 확인 후 제네릭 반영
    TypeName sagaActivityInterfaceTypeName = ClassName.get("com.senna.TemporalSage.saga", "SagaActivity");
    if (field.asType() instanceof DeclaredType declaredType) {
      var typeElement = (javax.lang.model.element.TypeElement) processingEnv.getTypeUtils().asElement(declaredType);
      for (TypeMirror tm : typeElement.getInterfaces()) {
        if (tm instanceof DeclaredType dt) {
          List<? extends TypeMirror> typeArgs = dt.getTypeArguments();
          if (typeArgs.size() == 2) {
            ClassName sagaActivityClassName = ClassName.get("com.senna.TemporalSage.saga", "SagaActivity");
            TypeName genericParam = ClassName.get(typeArgs.get(0));
            TypeName genericReturn = ClassName.get(typeArgs.get(1));
            sagaActivityInterfaceTypeName = ParameterizedTypeName.get(
                sagaActivityClassName,
                genericParam,
                genericReturn
            );
          }
        }
      }
    }

    return FieldSpec.builder(sagaActivityInterfaceTypeName, field.getSimpleName().toString())
        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
        .build();
  }

  /**
   * @Autowired 붙은 생성자
   */
  private static MethodSpec buildAutowiredConstructor(List<VariableElement> sagaActivityFields) {
    MethodSpec.Builder ctor = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Autowired.class);

    for (VariableElement field : sagaActivityFields) {
      TypeName fieldType = TypeName.get(field.asType());
      String fieldName = field.getSimpleName().toString();
      ctor.addParameter(fieldType, fieldName, Modifier.FINAL);
      ctor.addStatement("this.$N = $N", fieldName, fieldName);
    }
    return ctor.build();
  }

  /**
   * ActivityStub 생성자 (주어진 로직 그대로)
   */
  private static MethodSpec buildActivityStubConstructor(List<VariableElement> sagaActivityFields,
      javax.annotation.processing.ProcessingEnvironment processingEnv) {
    MethodSpec.Builder ctor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

    for (VariableElement sagaActivityField : sagaActivityFields) {
      Option options = sagaActivityField.asType().getAnnotation(Option.class);

      TypeName fieldType = TypeName.get(sagaActivityField.asType());
      String fieldName = sagaActivityField.getSimpleName().toString();

      var typeElement = (javax.lang.model.element.TypeElement) processingEnv
          .getTypeUtils().asElement(sagaActivityField.asType());
      String taskQueue = ((ClassName) fieldType).simpleName();

      // 원래 코드: OptionUtils.createActivityOptions(...) 호출
      ctor.addCode("OptionUtils.createActivityOptions($L, $S);\n", options, taskQueue);

      // SagaActivity<T,R> 제네릭 파악
      for (TypeMirror tm : typeElement.getInterfaces()) {
        if (tm instanceof DeclaredType declared) {
          List<? extends TypeMirror> typeArgs = declared.getTypeArguments();
          if (typeArgs.size() == 2) {
            TypeName genericParam = ClassName.get(typeArgs.get(0));
            TypeName genericReturn = ClassName.get(typeArgs.get(1));
            ctor.addStatement(
                "this.$N = ($T<$T, $T>) $T.newActivityStub($T.class, $N)",
                fieldName,
                ClassName.get("com.senna.TemporalSage.saga", "SagaActivity"),
                genericParam,
                genericReturn,
                Workflow.class,
                ClassName.get("com.senna.TemporalSage.saga", "SagaActivity"),
                taskQueue
            );
          }
        }
      }
    }

    return ctor.build();
  }

  /**
   * 워크플로우 메서드 구현부
   */
  private static MethodSpec buildWorkflowMethodImplementation(
      ExecutableElement methodElement,
      String methodName,
      MethodAnalyzer.ParsedMethodResult parsedResult
  ) {
    TypeName returnType = TypeName.get(methodElement.getReturnType());
    MethodSpec.Builder mb = MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC)
        .returns(returnType);

    // 메서드 파라미터
    for (VariableElement ve : methodElement.getParameters()) {
      mb.addParameter(TypeName.get(ve.asType()), ve.getSimpleName().toString(), Modifier.FINAL);
    }

    // Saga 생성
    mb.addStatement("$T saga = new $T(new $T.Builder().build())",
        Saga.class, Saga.class, Saga.Options.class);

    CodeBlock.Builder tryBlock = CodeBlock.builder()
        .addStatement("try {");

    // 보상 호출 등록
    for (MethodAnalyzer.CompensationCall cc : parsedResult.needCompensationCalls) {
      tryBlock.addStatement(cc.realStatement);
      String joinedArgs = String.join(", ", cc.arguments);
      tryBlock.addStatement("saga.addCompensation(() -> $L.compensate($N))", cc.scope, joinedArgs);
    }

    // catch 블럭
    tryBlock
        .addStatement("} catch (Exception e) {")
        .addStatement("  saga.compensate()")
        .addStatement("  throw new RuntimeException(e)")
        .addStatement("}");

    mb.addCode(tryBlock.build());

    // return 이 필요한 경우
    if (!returnType.toString().equals("void")) {
      mb.addStatement("return null");
    }

    return mb.build();
  }

  /**
   * 맨 앞 글자만 대문자로 만드는 헬퍼 (원래 로직 그대로)
   */
  private static String toUpperFirst(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }
}