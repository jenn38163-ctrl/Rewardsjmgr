import { Injectable, Logger } from '@nestjs/common';
import { ProviderAdapter, FulfillmentResult } from './provider.interface';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class GiftbitAdapter implements ProviderAdapter {
  private readonly logger = new Logger(GiftbitAdapter.name);
  private failureCounter = 0;

  constructor(private configService: ConfigService) {}

  async executeFulfillment(
    orderId: string,
    recipient: string,
    amountInr: number,
    itemType: 'GIFT_CARD' | 'ESIM_PLAN' | 'MOBILE_TOPUP'
  ): Promise<FulfillmentResult> {
    const apiKey = this.configService.get<string>('GIFTBIT_API_KEY');
    this.logger.log(`Giftbit Dispatching: Order=${orderId}, Recipient=${recipient}, Amount=₹${amountInr}`);

    // Simulate transient brand server failures (every 5th request) to test resiliency
    this.failureCounter++;
    if (this.failureCounter % 5 === 0 && !apiKey) {
      this.logger.warn(`Giftbit adapter simulating transient brand server outage (503 Service Unavailable)`);
      return {
        success: false,
        providerReference: '',
        error: 'Giftbit Server Outage - 503 Brand API Unavailable',
        isRetryable: true,
      };
    }

    // Standard high-fidelity mock response
    return {
      success: true,
      voucherCode: `GB-CLAIM-${Math.random().toString(36).substring(2, 10).toUpperCase()}`,
      voucherPin: `PIN-${Math.floor(1000 + Math.random() * 9000)}`,
      providerReference: `GB-REF-${Date.now()}`,
      isRetryable: false,
    };
  }
}
