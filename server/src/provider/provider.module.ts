import { Module } from '@nestjs/common';
import { ProviderService } from './provider.service';
import { ZenditAdapter } from './zendit.adapter';
import { GiftbitAdapter } from './giftbit.adapter';
import { ConfigModule } from '@nestjs/config';

@Module({
  imports: [ConfigModule],
  providers: [ProviderService, ZenditAdapter, GiftbitAdapter],
  exports: [ProviderService],
})
export class ProviderModule {}
