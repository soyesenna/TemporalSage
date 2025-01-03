package com.senna.TemporalSage.processor;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.auto.service.AutoService;
import com.senna.TemporalSage.annotations.EnableTemporalSage;
import com.senna.TemporalSage.annotations.Option;
import com.senna.TemporalSage.annotations.SageActivity;
import com.senna.TemporalSage.annotations.Workflowable;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.temporal.activity.ActivityOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.Workflow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes({"com.senna.TemporalSage.annotations.SageActivity"})
public class ActivityProcessor extends AbstractProcessor {

  private Messager messager;
  private Filer filer;

  private final List<Path> sourcePaths = new ArrayList<>();
  private final List<Path> classPaths = new ArrayList<>();
  private CombinedTypeSolver combinedTypeSolver;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.messager = processingEnv.getMessager();
    this.filer = processingEnv.getFiler();
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
    Set<? extends Element> springRootClass = roundEnv.getElementsAnnotatedWith(
        EnableTemporalSage.class);
    if (springRootClass.isEmpty()) {
      return false;
    }

    Set<? extends Element> sagaServiceClasses = roundEnv.getElementsAnnotatedWith(
        SageActivity.class);
    messager.printMessage(Kind.NOTE, "sagaServiceClasses: " + sagaServiceClasses);

    List<MethodSpec> activityBeans = new ArrayList<>();

    for (Element e : sagaServiceClasses) {
      if (!(e instanceof TypeElement)) {
        continue;
      }
      TypeElement sageActivity = (TypeElement) e;

      // 필드 스캔
      List<VariableElement> sagaActivityFields = findFields(sageActivity);
      Option options = sageActivity.getAnnotation(Option.class);

      activityBeans.add(this.createBeanDefinition(sageActivity, sagaActivityFields, options));
    }

    messager.printMessage(Kind.NOTE, "activityBeans: " + activityBeans);

    // 2) Configuration 클래스를 생성
    //    여기서는 예시로 "SageActivityConfiguration" 이라는 이름의 클래스
    TypeSpec configurationClass = TypeSpec.classBuilder("SageActivityConfiguration")
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Configuration.class)
        .addMethods(activityBeans)
        .build();

    String pkgName = processingEnv.getElementUtils()
        .getPackageOf(springRootClass.stream().findFirst().get()).getQualifiedName().toString();

    messager.printMessage(Kind.NOTE, "pkgName: " + pkgName);

    // 3) JavaFile 생성
    JavaFile javaFile = JavaFile.builder(pkgName + ".temporalsage.configuration.generated", configurationClass)
        .build();

    // 4) Filer에 write
    try {
      javaFile.writeTo(filer);
    } catch (IOException e1) {
      e1.printStackTrace();
    }

    return false;
  }

  private MethodSpec createBeanDefinition(TypeElement sageActivity,
      List<VariableElement> sagaActivityFields, Option options) {
    TypeName typeName = TypeName.get(sageActivity.asType());
    String methodName = this.getMethodName(sageActivity);
    MethodSpec.Builder builder =
        MethodSpec.methodBuilder(methodName + "Options")
            .addAnnotation(Bean.class)
            .addAnnotation(AnnotationSpec.builder(Qualifier.class).addMember("value", "$S", methodName + "Options").build())
            .addModifiers(Modifier.PUBLIC)
            .returns(ActivityOptions.class);

    for (VariableElement ve : sagaActivityFields) {
      builder.addParameter(TypeName.get(ve.asType()), ve.getSimpleName().toString(),
          Modifier.FINAL);
    }
    builder.addParameter(WorkerFactory.class, "workerFactory");

    builder.addStatement("$T worker = workerFactory.newWorker($S)", Worker.class, methodName);

    StringJoiner sj = new StringJoiner(", ");
    for (VariableElement ve : sagaActivityFields) {
      sj.add(ve.getSimpleName().toString());
    }
    String fields = sj.toString();

    builder.addStatement("$T sageActivityImpl = new $T($N)", typeName, typeName, fields);
    builder.addStatement("worker.registerActivitiesImplementations(sageActivityImpl)");

    builder.addCode(OptionUtils.createActivityOptions(options, methodName));

    builder.addStatement("return activityOptions");

    return builder.build();
//    builder.addStatement("return $T.newActivityStub($T.class, activityOptions)", Workflow.class, typeName);
//    return builder.build();
  }

  private List<VariableElement> findFields(TypeElement sageActivity) {
    List<VariableElement> fields = new ArrayList<>();
    for (Element enclosed : sageActivity.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.FIELD && enclosed instanceof VariableElement) {
        VariableElement ve = (VariableElement) enclosed;
        fields.add(ve);
      }
    }
    return fields;
  }

  private String getMethodName(TypeElement sageActivity) {
    String methodName = sageActivity.getSimpleName().toString();
    return methodName.substring(0, 1).toLowerCase() + methodName.substring(1);
  }
}
