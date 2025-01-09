package com.senna.TemporalSage.processor;

import com.senna.TemporalSage.saga.SagaActivity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

public class SagaActivityFieldFinder {

  private SagaActivityFieldFinder() {
  }

  public static List<VariableElement> findSagaActivityFields(TypeElement sagaServiceType, Messager messager) {
    List<VariableElement> fields = new ArrayList<>();
    for (Element enclosed : sagaServiceType.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.FIELD && enclosed instanceof VariableElement ve) {
        if (isSagaActivity(ve, messager)) {
          fields.add(ve);
        }
      }
    }
    return fields;
  }

  private static boolean isSagaActivity(VariableElement field, Messager messager) {
    TypeMirror type = field.asType();
    if (type.getKind() == TypeKind.DECLARED) {
      DeclaredType declaredType = (DeclaredType) type;
      Element element = declaredType.asElement();

      if (element instanceof TypeElement typeElement) {
        List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
        for (TypeMirror iface : interfaces) {
          Element ifaceElement = ((DeclaredType) iface).asElement();
          if (ifaceElement instanceof TypeElement ifaceTypeElement) {
            messager.printMessage(
                Diagnostic.Kind.NOTE,
                "Interface: " + ifaceTypeElement.getSimpleName()
            );

            boolean isSagaActivityInterface =
                ifaceTypeElement.getSimpleName().toString().equals(SagaActivity.class.getSimpleName());

            boolean hasActivityAnnotation =
                hasActivityInterfaceAnnotation(ifaceTypeElement, messager);

            if (isSagaActivityInterface && hasActivityAnnotation) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean hasActivityInterfaceAnnotation(TypeElement ifaceTypeElement, Messager messager) {
    List<? extends AnnotationMirror> annotationMirrors = ifaceTypeElement.getAnnotationMirrors();
    return annotationMirrors.stream().anyMatch(mirror -> {
      Element annotationTypeElement = mirror.getAnnotationType().asElement();
      if (annotationTypeElement instanceof TypeElement annoTypeElem) {
        messager.printMessage(Diagnostic.Kind.NOTE,
            "Annotation: " + annoTypeElem.getQualifiedName());
        return annoTypeElem.getQualifiedName().contentEquals("io.temporal.activity.ActivityInterface");
      }
      return false;
    });
  }
}