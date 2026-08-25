import { Injectable, CanActivate, ExecutionContext, UnauthorizedException, Logger } from '@nestjs/common';
import * as admin from 'firebase-admin';

@Injectable()
export class FirebaseAuthGuard implements CanActivate {
  private readonly logger = new Logger(FirebaseAuthGuard.name);
  private isFirebaseInitialized = false;

  constructor() {
    try {
      if (admin.apps.length === 0) {
        admin.initializeApp();
        this.logger.log('Successfully initialized Firebase Admin SDK.');
      }
      this.isFirebaseInitialized = true;
    } catch (e) {
      this.logger.warn(`Firebase Admin SDK uninitialized (expected in local/offline sandbox development): ${e.message}`);
    }
  }

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest();
    const authHeader = request.headers['authorization'];
    const isProduction = process.env.NODE_ENV === 'production';

    if (isProduction) {
      if (!authHeader || !authHeader.startsWith('Bearer ')) {
        throw new UnauthorizedException('Missing or invalid Authorization header in Production.');
      }
      if (!this.isFirebaseInitialized) {
        throw new UnauthorizedException('Security Configuration Pending: Firebase Admin SDK is uninitialized in Production.');
      }
    } else {
      if (!authHeader || !authHeader.startsWith('Bearer ')) {
        // Sandbox fallback for local developer testing
        const mockUser = request.headers['x-mock-user-id'] || request.headers['x-client-platform'];
        if (mockUser) {
          request.user = {
            uid: mockUser,
            email: 'developer@rewards.io',
            name: 'Developer Sandbox',
            role: 'ADMIN',
          };
          return true;
        }
        throw new UnauthorizedException('Missing or invalid Authorization header.');
      }
    }

    const token = authHeader.split(' ')[1];

    if (!this.isFirebaseInitialized) {
      // Offline mock verification if Firebase configuration is pending
      this.logger.warn(`Validating Bearer Token in simulated offline mode.`);
      request.user = {
        uid: 'sandbox_user_9921',
        email: 'sandbox@rewards.io',
        role: 'CUSTOMER',
      };
      return true;
    }

    try {
      const decodedToken = await admin.auth().verifyIdToken(token);
      request.user = {
        uid: decodedToken.uid,
        email: decodedToken.email,
        name: decodedToken.name,
        role: decodedToken.role || 'CUSTOMER',
      };
      return true;
    } catch (error) {
      this.logger.error(`Failed to verify Firebase ID Token: ${error.message}`);
      throw new UnauthorizedException(`Firebase token verification failed: ${error.message}`);
    }
  }
}
