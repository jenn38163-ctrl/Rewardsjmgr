import { Controller, Get, Post, Body, UseGuards, Request, HttpCode, HttpStatus, Logger } from '@nestjs/common';
import { WalletService } from './wallet.service';
import { FirebaseAuthGuard } from '../auth/auth.guard';

@Controller('wallet')
@UseGuards(FirebaseAuthGuard)
export class WalletController {
  private readonly logger = new Logger(WalletController.name);

  constructor(private readonly walletService: WalletService) {}

  @Get('balance')
  async getBalance(@Request() req) {
    const userId = req.user.uid;
    return await this.walletService.getBalance(userId);
  }

  @Post('topup')
  @HttpCode(HttpStatus.OK)
  async topUp(@Request() req, @Body('amountInr') amountInr: number) {
    const userId = req.user.uid;
    return await this.walletService.topUp(userId, amountInr);
  }

  @Get('audit')
  async getAuditTrail() {
    this.logger.log(`Fetching system double-entry audit logs...`);
    return await this.walletService.getAuditTrail();
  }

  @Get('accounts')
  async getChartOfAccounts() {
    this.logger.log(`Fetching chart of accounts...`);
    return await this.walletService.getChartOfAccounts();
  }
}
