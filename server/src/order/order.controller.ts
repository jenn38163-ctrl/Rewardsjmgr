import { Controller, Post, Get, Param, Body, UseGuards, Request, HttpCode, HttpStatus, Logger } from '@nestjs/common';
import { OrderService, CreateOrderDto } from './order.service';
import { FirebaseAuthGuard } from '../auth/auth.guard';

@Controller('orders')
@UseGuards(FirebaseAuthGuard)
export class OrderController {
  private readonly logger = new Logger(OrderController.name);

  constructor(private readonly orderService: OrderService) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  async placeOrder(@Request() req, @Body() body: Omit<CreateOrderDto, 'userId'>) {
    const userId = req.user.uid;
    this.logger.log(`Order placement requested by User=${userId}`);

    return await this.orderService.createOrder({
      ...body,
      userId,
    });
  }

  @Post(':id/retry')
  @HttpCode(HttpStatus.OK)
  async retryOrder(@Request() req, @Param('id') id: string) {
    const userId = req.user.uid;
    this.logger.log(`Manual order retry requested for Order=${id} by User=${userId}`);
    return await this.orderService.retryOrder(id, userId);
  }

  @Get()
  async getMyOrders(@Request() req) {
    const userId = req.user.uid;
    this.logger.log(`Fetching orders for User=${userId}`);
    return await this.orderService.getUserOrders(userId);
  }
}
