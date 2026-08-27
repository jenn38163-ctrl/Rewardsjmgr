import { Module, Global } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { DbService } from './db.service';
import { DbLogger, registerPoolListener } from '../utils/dbLogger';

@Global()
@Module({
  imports: [ConfigModule],
  providers: [DbService],
  exports: [DbService],
})
export class DbModule {}

export { DbLogger, registerPoolListener };
