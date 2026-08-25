import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { Logger, ValidationPipe } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

async function bootstrap() {
  const logger = new Logger('Bootstrap');
  const app = await NestFactory.create(AppModule);

  // Enable CORS for mobile clients
  app.enableCors({
    origin: '*',
    methods: 'GET,HEAD,PUT,PATCH,POST,DELETE,OPTIONS',
    credentials: true,
  });

  // Global Input Validation & Transformation
  app.useGlobalPipes(new ValidationPipe({
    whitelist: true,
    transform: true,
  }));

  // Swagger OpenAPI Documentation setup
  const config = new DocumentBuilder()
    .setTitle('Rewards Gateway API')
    .setDescription('Enterprise Rewards Gateway Backend API service with strict double-entry ledger audits and provider resilience.')
    .setVersion('1.0.0')
    .addBearerAuth()
    .build();
    
  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('api/docs', app, document);

  const port = process.env.PORT || 3000;
  await app.listen(port);
  
  logger.log(`=======================================================`);
  logger.log(`🚀 Rewards Gateway Server listening on PORT: ${port}`);
  logger.log(`📖 Swagger API Documentation live at: http://localhost:${port}/api/docs`);
  logger.log(`=======================================================`);
}

bootstrap().catch(err => {
  console.error('Fatal initialization error:', err);
  process.exit(1);
});
