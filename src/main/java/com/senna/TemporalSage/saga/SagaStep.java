package com.senna.TemporalSage.saga;

/**
 * SAGA 패턴의 각 단계를 정의하는 인터페이스 모든 Activity는 이 인터페이스를 구현해야 함
 */
public interface SagaStep<T, R> {

  /**
   * 정방향 트랜잭션 실행
   *
   * @param context 실행 컨텍스트
   * @return 실행 결과
   */
  R execute(T context);

  /**
   * 보상 트랜잭션 실행
   *
   * @param context 실행 컨텍스트
   */
  void compensate(T context);
} 