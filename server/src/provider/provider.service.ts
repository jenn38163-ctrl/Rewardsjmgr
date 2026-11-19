import { Injectable, Logger } from '@nestjs/common';
import { ZenditAdapter } from './zendit.adapter';
import { GiftbitAdapter } from './giftbit.adapter';
import { FulfillmentResult } from './provider.interface';
import { CircuitBreaker } from './circuit-breaker';

@Injectable()
export class ProviderService {
  private readonly logger = new Logger(ProviderService.name);
  private readonly zenditCircuit = new CircuitBreaker('Zendit', 3, 15000);
  private readonly giftbitCircuit = new CircuitBreaker('Giftbit', 3, 15000);

  constructor(
    private readonly zenditAdapter: ZenditAdapter,
    private readonly giftbitAdapter: GiftbitAdapter
  ) {}

  async fulfillOrder(
    orderId: string,
    recipient: string,
    amountInr: number,
    provider: 'ZENDIT' | 'GIFTBIT',
    itemType: 'GIFT_CARD' | 'ESIM_PLAN' | 'MOBILE_TOPUP'
  ): Promise<FulfillmentResult> {
    const circuit = provider === 'GIFTBIT' ? this.giftbitCircuit : this.zenditCircuit;
    const adapter = provider === 'GIFTBIT' ? this.giftbitAdapter : this.zenditAdapter;

    return circuit.execute(
      async () => {
        return await adapter.executeFulfillment(orderId, recipient, amountInr, itemType);
      },
      async (error) => {
        this.logger.error(`Circuit breaker fallback triggered for order ${orderId} (${provider}): ${error.message}`);
        return {
          success: false,
          providerReference: '',
          error: `Resilience block active: ${error.message}`,
          isRetryable: true,
        };
      }
    );
  }

  async getBalance(provider: 'ZENDIT' | 'GIFTBIT'): Promise<{ balance: number; currency: string }> {
    const adapter = provider === 'GIFTBIT' ? this.giftbitAdapter : this.zenditAdapter;
    if ('getBalance' in adapter && typeof (adapter as any).getBalance === 'function') {
      return await (adapter as any).getBalance();
    }
    throw new Error(`getBalance not supported for provider ${provider}`);
  }
}
