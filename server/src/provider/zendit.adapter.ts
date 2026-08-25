import { Injectable, Logger } from '@nestjs/common';
import { ProviderAdapter, FulfillmentResult } from './provider.interface';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class ZenditAdapter implements ProviderAdapter {
  private readonly logger = new Logger(ZenditAdapter.name);
  private failureCounter = 0;

  constructor(private configService: ConfigService) {}

  async executeFulfillment(
    orderId: string,
    recipient: string,
    amountInr: number,
    itemType: 'GIFT_CARD' | 'ESIM_PLAN' | 'MOBILE_TOPUP'
  ): Promise<FulfillmentResult> {
    const apiKey = this.configService.get<string>('ZENDIT_API_KEY');
    this.logger.log(`Zendit Dispatching: Order=${orderId}, Type=${itemType}, Recipient=${recipient}, Amount=₹${amountInr}`);

    // Simulate transient carrier failures (every 4th request) to test Circuit Breaker and Retryable state machines
    this.failureCounter++;
    if (this.failureCounter % 4 === 0 && !apiKey) {
      this.logger.warn(`Zendit adapter simulating transient network timeout for Order=${orderId}`);
      return {
        success: false,
        providerReference: '',
        error: 'Carrier Gateway Timeout (504) - Connection Closed by Remote Host',
        isRetryable: true,
      };
    }

    // Standard high-fidelity mock response
    if (itemType === 'ESIM_PLAN') {
      return {
        success: true,
        voucherCode: `LPA:1$rsp.zendit.io$ESIM-DISPATCH-${Math.random().toString(36).substring(2, 10).toUpperCase()}`,
        voucherPin: 'ActivationCode=123456',
        providerReference: `ZEN-REF-${Date.now()}`,
        isRetryable: false,
      };
    } else {
      // MOBILE_TOPUP
      return {
        success: true,
        voucherCode: `TOPUP-TXN-${Math.random().toString(36).substring(2, 10).toUpperCase()}`,
        voucherPin: 'N/A',
        providerReference: `ZEN-REF-${Date.now()}`,
        isRetryable: false,
      };
    }
  }
}
