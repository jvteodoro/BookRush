package com.bookrush.ingestion.jobs;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/** Explicit lifecycle for scheduler/worker roles; safe to resume after process restart. */
@Service
public class BootstrapPipeline {
  public enum State { IDLE, RUNNING, CANCEL_REQUESTED }
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
  public State start() { state.compareAndSet(State.IDLE, State.RUNNING); return state.get(); }
  public State cancel() { state.updateAndGet(s -> s == State.RUNNING ? State.CANCEL_REQUESTED : s); return state.get(); }
  public State state() { return state.get(); }
}
