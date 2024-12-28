package com.senna.TemporalSage.processor;

import com.google.auto.service.AutoService;
import com.senna.TemporalSage.annotations.SageService;
import com.senna.TemporalSage.annotations.Workflowable;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import io.temporal.workflow.Saga;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
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
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import org.springframework.stereotype.Component;

/**
 * Annotation Processor for SagaService, Workflowable, etc.
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21) // 예시로 Java17
@SupportedAnnotationTypes({
    "com.senna.TemporalSage.annotations.SageService"
})
public class SageProcessor extends AbstractProcessor {

  private Messager messager;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.messager = processingEnv.getMessager();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // 1) @SagaService 붙은 클래스 찾기
    Set<? extends Element> sagaServiceClasses = roundEnv.getElementsAnnotatedWith(
        SageService.class);

    for (Element classElement : sagaServiceClasses) {
      TypeElement sagaServiceType = (TypeElement) classElement;

      // TODO: 여기서 JavaParser로 파싱 가능한 형태로 뽑아오고싶음

      if (classElement instanceof TypeElement) {
        // === (A) 스캔: 필드 모으기 ===
        // 주로 sagaServiceType.getEnclosedElements() 중 FIELD 인 것들을 찾는다
        List<VariableElement> sagaActivityFields = new ArrayList<>();
        for (Element enclosed : sagaServiceType.getEnclosedElements()) {
          if (enclosed.getKind() == ElementKind.FIELD
              && enclosed instanceof VariableElement) {

            VariableElement fieldElement = (VariableElement) enclosed;
            // 타입이 SagaActivity 구현체인지 체크
            // 간단하게 "SagaActivity"라는 이름을 포함하는지,
            // 혹은 인터페이스 체인으로 구현 여부를 확인할 수도 있음
            if (isSagaActivity(fieldElement)) {
              sagaActivityFields.add(fieldElement);
            }
          }
        }
        // 2) 클래스 내부 메서드(Elements) 순회,
        //    @Workflowable 붙은 메서드 찾기
        for (Element enclosed : sagaServiceType.getEnclosedElements()) {
          // 메서드인지?
          if (enclosed instanceof ExecutableElement) {
            ExecutableElement methodElement = (ExecutableElement) enclosed;

            // @Workflowable 붙어있는지 체크
            Workflowable workflowable = methodElement.getAnnotation(Workflowable.class);
            if (workflowable != null) {
              // 4) 여기서 "워크플로우 인터페이스 + 구현체"를
              //    생성하는 로직을 호출
              generateWorkflowCode(sagaServiceType, methodElement, sagaActivityFields);
            }
          }
        }
      }
    }

    return false;
  }

  /**
   * 간단한 체크: 'fieldElement'가 SagaActivity를 구현하는지
   */
  private boolean isSagaActivity(VariableElement fieldElement) {
    // TODO: 인터페이스 체인으로 구현 여부를 체크
    // (1) 직접 "SagaActivity" 인터페이스 명이 맞는지?
    // (2) 좀 더 정확히는 Types.isAssignable(...) 로 상속/구현 관계 체크
    TypeMirror fieldType = fieldElement.asType();
    // 예시: if (fieldType.toString().contains("SagaActivity")) return true;
    // or do advanced check with processingEnv.getTypeUtils().isAssignable(...)
    return fieldType.toString().contains("SagaActivity");
  }

  /**
   * 실제로 JavaPoet 등을 사용해 인터페이스와 구현체를 생성하는 메서드
   */
  private void generateWorkflowCode(
      TypeElement sagaServiceClass,
      ExecutableElement workflowableMethod,
      List<VariableElement> sagaActivityFields
  ) {
    String methodName = workflowableMethod.getSimpleName().toString();
    String interfaceName = toUpperFirst(methodName) + "WorkflowInterface";
    String implName = toUpperFirst(methodName) + "WorkflowInterfaceImpl";

    // (1) Workflow Interface
    TypeSpec workflowInterfaceSpec = createWorkflowInterfaceSpec(interfaceName, workflowableMethod,
        methodName);

    // (2) Workflow Implementation (여기서 필드 & 생성자 & 메서드 추가)
    TypeSpec workflowImplSpec = createWorkflowImplSpec(
        implName,
        interfaceName,
        workflowableMethod,
        methodName,
        sagaActivityFields
    );

    String packageName = processingEnv.getElementUtils()
        .getPackageOf(sagaServiceClass)
        .getQualifiedName().toString();

    try {
      JavaFile interfaceFile = JavaFile.builder(packageName, workflowInterfaceSpec).build();
      interfaceFile.writeTo(processingEnv.getFiler());

      JavaFile implFile = JavaFile.builder(packageName, workflowImplSpec).build();
      implFile.writeTo(processingEnv.getFiler());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  private TypeSpec createWorkflowInterfaceSpec(
      String interfaceName,
      ExecutableElement workflowableMethod,
      String methodName
  ) {
    return TypeSpec.interfaceBuilder(interfaceName)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(createWorkflowInterfaceAnnotationSpec())
        .addMethod(createWorkflowMethodSpec(workflowableMethod, methodName))
        .build();
  }

  private TypeSpec createWorkflowImplSpec(
      String implName,
      String interfaceName,
      ExecutableElement methodElement,
      String methodName,
      List<VariableElement> sagaActivityFields
  ) {
    // 1) Builder
    TypeSpec.Builder implBuilder = TypeSpec.classBuilder(implName)
        .addModifiers(Modifier.PUBLIC)
        .addSuperinterface(ClassName.get("", interfaceName))
        .addAnnotation(createComponentAnnotationSpec());

    // 2) Add Fields
    //    for each activity field in sagaActivityFields
    for (VariableElement field : sagaActivityFields) {
      FieldSpec fieldSpec = FieldSpec.builder(
              ClassName.get(field.asType()),  // field type
              field.getSimpleName().toString(), // same name as original
              Modifier.PRIVATE, Modifier.FINAL
          )
          .build();
      implBuilder.addField(fieldSpec);
    }

    // 3) Create a constructor that takes all these fields as params
    MethodSpec constructorSpec = createConstructorWithActivities(sagaActivityFields);
    implBuilder.addMethod(constructorSpec);

    // 4) Add the workflow method implementation
    MethodSpec workflowMethodImpl = createWorkflowMethodImplSpec(methodElement, methodName);
    implBuilder.addMethod(workflowMethodImpl);

    return implBuilder.build();
  }

  private MethodSpec createConstructorWithActivities(List<VariableElement> sagaActivityFields) {
    MethodSpec.Builder ctorBuilder = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PUBLIC);

    // for each field, add parameter & assignment
    for (VariableElement field : sagaActivityFields) {
      // TODO: 여기서 Activity Stub을 생성한다. 이때, 이 SagaActivity interface 구현체에 ActivityOptions annotation이 붙어있는지 확인하고 있으면 해당 옵션들을 적용해준다.
      String fieldName = field.getSimpleName().toString();
      ctorBuilder
          .addParameter(ClassName.get(field.asType()), fieldName)
          .addStatement("this.$N = $N", fieldName, fieldName);
    }

    return ctorBuilder.build();
  }

  /**
   * @WorkflowMethod annotation을 붙인 메서드 시그니처를 만드는 예시
   */
  private MethodSpec createWorkflowMethodSpec(ExecutableElement methodElement, String methodName) {
    // 리턴 타입 / 파라미터 타입 등을 분석해서 MethodSpec 작성
    return MethodSpec.methodBuilder(methodName)
        .returns(ClassName.get(methodElement.getReturnType()))
        // TODO: 파라미터도 추가해야 함
        // ...
        .addAnnotation(createWorkflowMethodAnnotationSpec()) // @WorkflowMethod
        .build();
  }

  /**
   * 구현체 쪽에서 실제 메서드 로직(예시)을 생성
   */
  private MethodSpec createWorkflowMethodImplSpec(ExecutableElement methodElement,
      String methodName) {
    // 아래는 "간단한 목업"이므로, 실제론
    // saga.addCompensation(...) 호출, Activity Stub 생성 등 복잡 로직을 넣을 수 있음.
    return MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC)
        .returns(ClassName.get(methodElement.getReturnType()))
        .addStatement(createSagaInstanceCodeBlock())
        .addStatement(createRealMethodBodyCodeBlock())
        .addStatement("return null") // 임시
        .build();
  }

  private CodeBlock createSagaInstanceCodeBlock() {
    return CodeBlock.builder()
        .addStatement("$T saga = new $T();", Saga.class, Saga.class)
        .build();
  }

  private CodeBlock createRealMethodBodyCodeBlock() {
    return CodeBlock.builder()
        .addStatement("try {")
        .addStatement("// TODO: 원래 메서드의 구현 로직 복사(java or class 파일을 직접 읽어서 복사) 및 saga instance에 보상 로직(SagaActivity 구현 인스턴스의 compensate() 메서드) 추가")
        .addStatement("// TODO: ")
        .addStatement("} catch (Exception e) {saga.compensate();}")
        .build();
  }

  /**
   * 유틸: "hello" -> "Hello"
   */
  private String toUpperFirst(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }

  private AnnotationSpec createWorkflowInterfaceAnnotationSpec() {
    return AnnotationSpec.builder(WorkflowInterface.class).build();
  }

  private AnnotationSpec createWorkflowMethodAnnotationSpec() {
    return AnnotationSpec.builder(WorkflowMethod.class).build();
  }

  private AnnotationSpec createComponentAnnotationSpec() {
    return AnnotationSpec.builder(Component.class).build();
  }
}