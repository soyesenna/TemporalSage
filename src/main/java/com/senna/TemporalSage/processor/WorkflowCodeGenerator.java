package com.senna.TemporalSage.processor;

import com.senna.TemporalSage.annotations.GeneratedWorkflow;
import com.senna.TemporalSage.annotations.Option;
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
import java.util.List;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

public class WorkflowCodeGenerator {

  private WorkflowCodeGenerator() {
  }

  public static void generateWorkflowCode(
      TypeElement sagaServiceClass,
      ExecutableElement workflowableMethod,
      List<VariableElement> sagaActivityFields,
      MethodAnalyzer.ParsedMethodResult parsedResult,
      Messager messager,
      Filer filer,
      javax.annotation.processing.ProcessingEnvironment processingEnv
  ) {
    if (parsedResult.hasDeterminismError) {
      for (String err : parsedResult.determinismErrors) {
        messager.printMessage(Diagnostic.Kind.ERROR, "[SageProcessor] " + err);
        return;
      }
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

    for (VariableElement field : sagaActivityFields) {
      implBuilder.addField(buildSagaActivityFieldSpec(field, processingEnv));
    }

    implBuilder.addMethod(buildAutowiredConstructor(sagaActivityFields));
    implBuilder.addMethod(buildActivityStubConstructor(sagaActivityFields, processingEnv));

    implBuilder.addMethod(buildWorkflowMethodImplementation(methodElement, methodName, parsedResult));

    return implBuilder.build();
  }

  private static FieldSpec buildSagaActivityFieldSpec(VariableElement field, javax.annotation.processing.ProcessingEnvironment processingEnv) {
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

  private static MethodSpec buildActivityStubConstructor(List<VariableElement> sagaActivityFields,
      javax.annotation.processing.ProcessingEnvironment processingEnv) {
    MethodSpec.Builder ctor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

    for (VariableElement sagaActivityField : sagaActivityFields) {
      Option options = sagaActivityField.asType().getAnnotation(Option.class);

      TypeName fieldType = TypeName.get(sagaActivityField.asType());
      String fieldName = sagaActivityField.getSimpleName().toString();

      String taskQueue = ((ClassName) fieldType).simpleName();

      ctor.addCode(OptionUtils.createActivityOptions(options, taskQueue));
      ctor.addStatement("this.$N = $T.newActivityStub($T.class, $N);",
          fieldName, Workflow.class, ClassName.get("com.senna.TemporalSage.saga", "SagaActivity"), taskQueue);
    }

    return ctor.build();
  }

  private static MethodSpec buildWorkflowMethodImplementation(
      ExecutableElement methodElement,
      String methodName,
      MethodAnalyzer.ParsedMethodResult parsedResult
  ) {
    TypeName returnType = TypeName.get(methodElement.getReturnType());
    MethodSpec.Builder mb = MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC)
        .returns(returnType);

    for (VariableElement ve : methodElement.getParameters()) {
      mb.addParameter(TypeName.get(ve.asType()), ve.getSimpleName().toString(), Modifier.FINAL);
    }

    mb.addStatement("$T saga = new $T(new $T.Builder().build())",
        Saga.class, Saga.class, Saga.Options.class);

    CodeBlock.Builder tryBlock = CodeBlock.builder()
        .addStatement("try {");

    for (MethodAnalyzer.CompensationCall cc : parsedResult.needCompensationCalls) {
      tryBlock.addStatement(cc.realStatement);
      String joinedArgs = String.join(", ", cc.arguments);
      tryBlock.addStatement("saga.addCompensation(() -> $L.compensate($N))", cc.scope, joinedArgs);
    }

    tryBlock
        .addStatement("} catch (Exception e) {")
        .addStatement("  saga.compensate()")
        .addStatement("  throw new RuntimeException(e)")
        .addStatement("}");

    mb.addCode(tryBlock.build());

    if (!returnType.toString().equals("void")) {
      mb.addStatement("return null");
    }

    return mb.build();
  }

  private static String toUpperFirst(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }
}