import { Controller, Post, Body, Headers, HttpCode, HttpStatus, Logger, UnauthorizedException } from '@nestjs/common';
import { WebhookService, LocalWebhookEventDto } from './webhook.service';

@Controller('webhooks')
export class WebhookController {
  private readonly logger = new Logger(WebhookController.name);

  constructor(private readonly webhookService: WebhookService) {}

  @Post('provider')
  @HttpCode(HttpStatus.OK)
  async handleProviderWebhook(
    @Headers('x-provider-signature') signature: string,
    @Body() event: LocalWebhookEventDto
  ) {
    this.logger.log(`Incoming webhook hook from: ${event.provider}`);
    
    // In production, verify signature against webhook signing secret key
    if (signature && signature === 'invalid') {
      throw new UnauthorizedException('Signature mismatch: Webhook tampered or unauthorized.');
    }

    return await this.webhookService.handleIncomingWebhook(event);
  }
}
