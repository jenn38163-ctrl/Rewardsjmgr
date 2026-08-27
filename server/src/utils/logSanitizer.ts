/**
 * Log Sanitization Utility
 * Redacts and masks sensitive fields including:
 * - Credentials (passwords, tokens, API keys, secrets, authorization headers, database connection URIs)
 * - Voucher data (voucher codes, claim codes, gift card numbers, PINs, CVVs)
 * - PII (emails, phone numbers, SSNs, Aadhaar, PAN, identity numbers, addresses)
 * before reaching stdout, stderr, or log streams.
 */

// Key names that should be automatically redacted when encountered in objects/payloads
export const SENSITIVE_KEY_PATTERNS = [
  // Credentials & Secrets
  'password',
  'passwd',
  'pass',
  'pwd',
  'secret',
  'token',
  'authtoken',
  'auth_token',
  'jwt',
  'bearer',
  'apikey',
  'api_key',
  'auth',
  'authorization',
  'credential',
  'credentials',
  'privatekey',
  'private_key',
  'priv_key',
  'accesstoken',
  'access_token',
  'refreshtoken',
  'refresh_token',
  'sessiontoken',
  'session_token',
  'idtoken',
  'id_token',
  'encryptionsecret',
  'encryption_secret',
  'signature',

  // Voucher Numbers, Cards & Financial
  'vouchercode',
  'voucher_code',
  'vouchernumber',
  'voucher_number',
  'voucher',
  'voucherpin',
  'voucher_pin',
  'claimcode',
  'claim_code',
  'claimpin',
  'claim_pin',
  'cardnumber',
  'card_number',
  'giftcardcode',
  'gift_card_code',
  'giftcard',
  'gift_card',
  'pin',
  'cvv',
  'cvc',
  'accountnumber',
  'account_number',
  'routingnumber',
  'routing_number',
  'iban',
  'bban',

  // User Identifiers & Personally Identifiable Information (PII)
  'userid',
  'user_id',
  'uid',
  'customerid',
  'customer_id',
  'customertoken',
  'customer_token',
  'email',
  'phone',
  'phonenumber',
  'phone_number',
  'mobile',
  'mobilenumber',
  'mobile_number',
  'telephone',
  'ssn',
  'socialsecurity',
  'social_security',
  'aadhaar',
  'aadhar',
  'pan',
  'pancard',
  'pan_card',
  'passport',
  'passportnumber',
  'passport_number',
  'nationalid',
  'national_id',
  'drivinglicense',
  'driving_license',
  'dob',
  'dateofbirth',
  'date_of_birth',
  'address',
  'streetaddress',
  'street_address',
];

// Regex patterns for string sanitization
const DATABASE_URI_REGEX = /(postgres(?:ql)?|mysql|mongodb(?:\+srv)?|redis|amqp(?:s)?):\/\/([^:]+):([^@]+)@/gi;
const BEARER_AUTH_REGEX = /(Bearer\s+)[A-Za-z0-9_\-\.=]+/gi;
const BASIC_AUTH_REGEX = /(Basic\s+)[A-Za-z0-9+/=]+/gi;
const JWT_REGEX = /\beyJ[A-Za-z0-9_\-]{8,}\.eyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\b/g;

// Key-Value pattern in string messages (e.g. `password="secret"`, `"voucherCode": "1234-5678"`, `apiKey=xyz`)
const INLINE_KEY_VALUE_REGEX = new RegExp(
  `([\\b"']?(?:${SENSITIVE_KEY_PATTERNS.join('|')})[\\b"']?\\s*[:=]\\s*["']?)([^"',;\\s&]+)(["']?)`,
  'gi'
);

// Standard formatted Credit / Gift Card Numbers (13-19 digits, with hyphens/spaces)
const CARD_NUMBER_REGEX = /\b(?:\d[ -]*?){13,19}\b/g;

// US SSN format: 000-00-0000
const SSN_REGEX = /\b\d{3}-\d{2}-\d{4}\b/g;

// Indian PAN card format: 5 letters + 4 digits + 1 letter (e.g. ABCDE1234F)
const PAN_REGEX = /\b[A-Z]{5}[0-9]{4}[A-Z]\b/g;

// Indian Aadhaar number format: 4 digits + space + 4 digits + space + 4 digits
const AADHAAR_REGEX = /\b\d{4}\s\d{4}\s\d{4}\b/g;

// Email addresses
const EMAIL_REGEX = /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/g;

// Phone numbers with international or standard local format
const PHONE_REGEX = /(?:\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b/g;

/**
 * Partially masks an email (e.g., john.doe@example.com -> j***e@example.com)
 */
export function maskEmail(email: string): string {
  const parts = email.split('@');
  if (parts.length !== 2) return '*****';
  const [name, domain] = parts;
  if (name.length <= 2) {
    return `${name[0] || '*'}***@${domain}`;
  }
  return `${name[0]}***${name[name.length - 1]}@${domain}`;
}

/**
 * Masks a credit card / gift card / voucher number, showing only last 4 characters
 */
export function maskVoucherOrCard(value: string): string {
  const cleaned = value.replace(/[\s-]/g, '');
  if (cleaned.length < 6) return '*****';
  const last4 = cleaned.slice(-4);
  return `****-****-****-${last4}`;
}

/**
 * Masks a phone number
 */
export function maskPhoneNumber(phone: string): string {
  const cleaned = phone.replace(/[\s().-]/g, '');
  if (cleaned.length < 4) return '*****';
  return `***-***-${cleaned.slice(-4)}`;
}

/**
 * Sanitizes raw string log messages by replacing passwords, database URIs,
 * authorization headers, JWTs, voucher codes, emails, phones, and national IDs.
 */
export function sanitizeLogMessage(message: string): string {
  if (!message || typeof message !== 'string') return message;

  let result = message;

  // 1. Redact DB URIs (e.g., postgresql://user:secret@host:5432/db -> postgresql://user:*****@host:5432/db)
  result = result.replace(DATABASE_URI_REGEX, '$1://$2:*****@');

  // 2. Redact Authorization Headers
  result = result.replace(BEARER_AUTH_REGEX, '$1*****');
  result = result.replace(BASIC_AUTH_REGEX, '$1*****');

  // 3. Redact JWT tokens
  result = result.replace(JWT_REGEX, '[REDACTED_JWT]');

  // 4. Redact Key-Value pairs matching sensitive keys in string text
  result = result.replace(INLINE_KEY_VALUE_REGEX, (match, prefix, val, suffix) => {
    // If already masked, skip
    if (val === '*****' || val === '[REDACTED]') return match;
    return `${prefix}*****${suffix}`;
  });

  // 5. Redact SSNs
  result = result.replace(SSN_REGEX, '***-**-****');

  // 6. Redact Aadhaar Numbers
  result = result.replace(AADHAAR_REGEX, '**** **** ****');

  // 7. Redact PAN Cards
  result = result.replace(PAN_REGEX, '*****[PAN]');

  // 8. Mask Credit / Voucher / Gift Card numbers
  result = result.replace(CARD_NUMBER_REGEX, (card) => {
    const digitsOnly = card.replace(/\D/g, '');
    if (digitsOnly.length >= 13 && digitsOnly.length <= 19) {
      return maskVoucherOrCard(card);
    }
    return card;
  });

  // 9. Mask Emails
  result = result.replace(EMAIL_REGEX, (email) => maskEmail(email));

  return result;
}

/**
 * Deeply sanitizes JavaScript objects, arrays, and errors by masking
 * any fields matching sensitive key names and sanitizing string values.
 */
export function sanitizeLogObject<T = any>(obj: T, visited = new WeakSet()): T {
  if (obj === null || obj === undefined) {
    return obj;
  }

  if (typeof obj === 'string') {
    return sanitizeLogMessage(obj) as unknown as T;
  }

  if (typeof obj !== 'object') {
    return obj;
  }

  if (obj instanceof Date || obj instanceof RegExp) {
    return obj;
  }

  if (obj instanceof Error) {
    const errorObj: Record<string, any> = {
      name: obj.name,
      message: sanitizeLogMessage(obj.message),
      stack: obj.stack ? sanitizeLogMessage(obj.stack) : undefined,
    };
    for (const key of Object.keys(obj)) {
      if (!['name', 'message', 'stack'].includes(key)) {
        errorObj[key] = sanitizeLogObject((obj as any)[key]);
      }
    }
    return errorObj as unknown as T;
  }

  // Prevent circular references
  if (visited.has(obj as object)) {
    return '[CIRCULAR_REF]' as unknown as T;
  }
  visited.add(obj as object);

  if (Array.isArray(obj)) {
    return obj.map((item) => sanitizeLogObject(item, visited)) as unknown as T;
  }

  const sanitized: Record<string, any> = {};

  for (const [key, value] of Object.entries(obj)) {
    const lowerKey = key.toLowerCase().replace(/[^a-z0-9]/g, '');
    const isSensitive = SENSITIVE_KEY_PATTERNS.some((pattern) => {
      const cleanPattern = pattern.toLowerCase().replace(/[^a-z0-9]/g, '');
      return lowerKey === cleanPattern || lowerKey.includes(cleanPattern);
    });

    if (isSensitive) {
      if (typeof value === 'string') {
        // Apply specialized masking if voucher, email, or card
        if (lowerKey.includes('email')) {
          sanitized[key] = maskEmail(value);
        } else if (lowerKey.includes('card') || lowerKey.includes('voucher') || lowerKey.includes('claim')) {
          sanitized[key] = maskVoucherOrCard(value);
        } else {
          sanitized[key] = '*****';
        }
      } else if (typeof value === 'number') {
        sanitized[key] = '*****';
      } else {
        sanitized[key] = '*****';
      }
    } else if (typeof value === 'string') {
      sanitized[key] = sanitizeLogMessage(value);
    } else if (typeof value === 'object') {
      sanitized[key] = sanitizeLogObject(value, visited);
    } else {
      sanitized[key] = value;
    }
  }

  return sanitized as T;
}

/**
 * Universal sanitization function that safely processes strings, objects, arrays,
 * errors, or primitive values before log output.
 */
export function sanitizeLog(input: any): any {
  if (typeof input === 'string') {
    return sanitizeLogMessage(input);
  }
  return sanitizeLogObject(input);
}

/**
 * Sanitizes a connection URI string to ensure passwords and credentials are never leaked in logs.
 */
export function sanitizeConnectionUri(uri?: string): string {
  if (!uri) return '[UNDEFINED]';
  return uri.replace(/(postgres(?:ql)?|mysql|mongodb(?:\+srv)?|redis|amqp(?:s)?):\/\/([^:]+):([^@]+)@/gi, '$1://$2:*****@');
}

/**
 * Strips and masks sensitive fields from objects before logging.
 */
export function maskSensitiveFields<T extends Record<string, any>>(obj: T): T {
  return sanitizeLogObject(obj);
}

/**
 * Creates a wrapped console/logger output function that guarantees
 * all arguments are sanitized before reaching stdout/stderr.
 */
export function createSanitizedLogger(context: string) {
  return {
    log: (...args: any[]) => {
      const sanitized = args.map((arg) => sanitizeLog(arg));
      console.log(`[${context}]`, ...sanitized);
    },
    info: (...args: any[]) => {
      const sanitized = args.map((arg) => sanitizeLog(arg));
      console.info(`[${context}]`, ...sanitized);
    },
    warn: (...args: any[]) => {
      const sanitized = args.map((arg) => sanitizeLog(arg));
      console.warn(`[${context}]`, ...sanitized);
    },
    error: (...args: any[]) => {
      const sanitized = args.map((arg) => sanitizeLog(arg));
      console.error(`[${context}]`, ...sanitized);
    },
    debug: (...args: any[]) => {
      const sanitized = args.map((arg) => sanitizeLog(arg));
      console.debug(`[${context}]`, ...sanitized);
    },
  };
}
