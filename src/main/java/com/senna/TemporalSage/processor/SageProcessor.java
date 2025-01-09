package com.senna.TemporalSage.processor;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.auto.service.AutoService;
import com.senna.TemporalSage.annotations.SageService;
import com.senna.TemporalSage.annotations.Workflowable;
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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

/**
 * 리팩터링된 SageProcessor
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes({"com.senna.TemporalSage.annotations.SageService"})
public class SageProcessor extends AbstractProcessor {

  private Messager messager;
  private Filer filer;
  private ProcessingEnvironment processingEnv;

  private final List<Path> sourcePaths = new ArrayList<>();
  private final List<Path> classPaths = new ArrayList<>();
  private CombinedTypeSolver combinedTypeSolver;

  // 로컬에서 변수 이름을 위한 카운터 (주어진 로직 그대로 유지)
  private int variableCount = 0;

  // "execute" -> "compensate" 매핑 (주어진 로직 그대로 유지)
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

    // 경로 설정 로직 (주어진 로직 그대로 유지)
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

    // 심볼 리졸버 설정 (주어진 로직 그대로 유지)
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
    // 1. @SageService 로 클래스가 주어졌을 때 처리
    Set<? extends Element> sagaServiceClasses = roundEnv.getElementsAnnotatedWith(SageService.class);
    for (Element element : sagaServiceClasses) {
      if (!(element instanceof TypeElement)) {
        continue;
      }
      TypeElement sagaServiceType = (TypeElement) element;

      // 2. SagaActivity 필드 찾기
      List<VariableElement> sagaActivityFields =
          SagaActivityFieldFinder.findSagaActivityFields(sagaServiceType, messager);

      // 필드가 없으면 에러 메시지
      if (sagaActivityFields.isEmpty()) {
        messager.printMessage(Diagnostic.Kind.ERROR,
            "SagaActivity fields are not found, Is it workflow?", element);
      }

      // 3. @Workflowable 메서드 분석
      for (Element enclosed : sagaServiceType.getEnclosedElements()) {
        if (enclosed instanceof ExecutableElement executableElement) {
          Workflowable wf = executableElement.getAnnotation(Workflowable.class);
          if (wf != null) {
            // 메서드 본문 파싱 및 분석
            MethodAnalyzer.ParsedMethodResult result =
                MethodAnalyzer.parseMethodAndAnalyzeActivityCalls(
                    sagaServiceType,
                    executableElement,
                    sagaActivityFields,
                    sourcePaths,
                    combinedTypeSolver,
                    messager,
                    this::getVariableName,
                    ACTIVITY_COMPENSATION_MAP
                );

            // 4. 워크플로우 코드 생성
            WorkflowCodeGenerator.generateWorkflowCode(
                sagaServiceType,
                executableElement,
                sagaActivityFields,
                result,
                messager,
                filer,
                processingEnv
            );
          }
        }
      }
    }
    return false;
  }

  /**
   * 변수 이름을 생성하기 위한 메서드 (원래 로직 유지)
   */
  private String getVariableName() {
    return "var" + this.variableCount++;
  }
}