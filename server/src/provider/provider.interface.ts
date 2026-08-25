export interface FulfillmentResult {
  success: boolean;
  voucherCode?: string;
  voucherPin?: string;
  providerReference: string;
  error?: string;
  isRetryable: boolean;
}

export interface ProviderAdapter {
  executeFulfillment(
    orderId: string,
    recipient: string,
    amountInr: number,
    itemType: 'GIFT_CARD' | 'ESIM_PLAN' | 'MOBILE_TOPUP'
  ): Promise<FulfillmentResult>;
}
