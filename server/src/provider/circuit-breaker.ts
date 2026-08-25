import { Logger, ServiceUnavailableException } from '@nestjs/common';

export enum CircuitState {
  CLOSED,
  OPEN,
  HALF_OPEN,
}

export class CircuitBreaker {
  private readonly logger = new Logger(CircuitBreaker.name);
  private state = CircuitState.CLOSED;
  private failureCount = 0;
  private lastFailureTime = 0;

  constructor(
    private readonly name: string,
    private readonly failureThreshold = 3,
    private readonly resetTimeoutMs = 15000
  ) {}

  async execute<T>(block: () => Promise<T>, fallback: (err: Error) => Promise<T>): Promise<T> {
    const now = Date.now();

    // Transition from OPEN to HALF_OPEN after timeout
    if (this.state === CircuitState.OPEN) {
      if (now - this.lastFailureTime > this.resetTimeoutMs) {
        this.logger.warn(`Circuit [${this.name}] cooled down. Transitioning OPEN -> HALF_OPEN`);
        this.state = CircuitState.HALF_OPEN;
      } else {
        this.logger.error(`Circuit [${this.name}] is OPEN. Fast-failing downstream request.`);
        return fallback(new Error(`Circuit [${this.name}] is open`));
      }
    }

    try {
      const result = await block();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure(error);
      return fallback(error);
    }
  }

  private onSuccess() {
    this.failureCount = 0;
    if (this.state !== CircuitState.CLOSED) {
      this.logger.log(`Circuit [${this.name}] call succeeded. Transitioning to CLOSED.`);
      this.state = CircuitState.CLOSED;
    }
  }

  private onFailure(error: Error) {
    this.lastFailureTime = Date.now();
    this.failureCount++;
    this.logger.error(`Circuit [${this.name}] recorded failure #${this.failureCount}: ${error.message}`);

    if (this.failureCount >= this.failureThreshold) {
      this.state = CircuitState.OPEN;
      this.logger.error(`Circuit [${this.name}] has TRIPPED! State is now OPEN.`);
    }
  }
}
