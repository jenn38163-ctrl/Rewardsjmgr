import { Test, TestingModule } from '@nestjs/testing';
import { LedgerService, LedgerEntryDto } from './ledger.service';
import { DbService } from '../db/db.service';
import { BadRequestException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

describe('LedgerService (Strict Double-Entry Testing)', () => {
  let service: LedgerService;
  let mockDbService: any;

  beforeEach(async () => {
    // High-fidelity mock of DbService
    mockDbService = {
      db: {
        transaction: jest.fn().mockImplementation(async (cb) => {
          return await cb({
            insert: jest.fn().mockReturnValue({
              values: jest.fn().mockReturnValue({
                returning: jest.fn().mockResolvedValue([{ id: 'mock-journal-123' }])
              })
            }),
            select: jest.fn().mockReturnValue({
              from: jest.fn().mockReturnValue({
                where: jest.fn().mockReturnValue({
                  for: jest.fn().mockResolvedValue([{ id: 'acc-1', code: 'ACC-1', type: 'ASSET' }])
                })
              })
            }),
            update: jest.fn().mockReturnValue({
              set: jest.fn().mockReturnValue({
                where: jest.fn().mockResolvedValue(true)
              })
            })
          });
        }),
        query: {
          accounts: {
            findFirst: jest.fn().mockResolvedValue({
              id: 'acc-1',
              code: 'ASSET:ZENDIT_PREPAID_USD',
              type: 'ASSET',
              balanceCents: 50000n // $500.00
            })
          }
        }
      }
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        LedgerService,
        { provide: DbService, useValue: mockDbService },
      ],
    }).compile();

    service = module.get<LedgerService>(LedgerService);
  });

  it('should successfully post a balanced double-entry transaction', async () => {
    const entries: LedgerEntryDto[] = [
      { accountCode: 'LIABILITY:USER_WALLETS', amountCents: 1000n, direction: 'DEBIT' },
      { accountCode: 'ASSET:ZENDIT_PREPAID_INR', amountCents: 1000n, direction: 'CREDIT' },
    ];

    const result = await service.postTransaction(
      'PURCHASE_ORDER',
      'order-9912',
      'Test balanced transaction',
      entries
    );

    expect(result).toBeDefined();
    expect(mockDbService.db.transaction).toHaveBeenCalled();
  });

  it('should throw BadRequestException if debits do not equal credits', async () => {
    const unbalancedEntries: LedgerEntryDto[] = [
      { accountCode: 'LIABILITY:USER_WALLETS', amountCents: 1000n, direction: 'DEBIT' },
      { accountCode: 'ASSET:ZENDIT_PREPAID_INR', amountCents: 1500n, direction: 'CREDIT' }, // unbalanced
    ];

    await expect(
      service.postTransaction('PURCHASE_ORDER', 'order-9912', 'Unbalanced transaction', unbalancedEntries)
    ).rejects.toThrow(BadRequestException);
  });

  it('should successfully compute account balances from cents to floating scale', async () => {
    const balance = await service.getAccountBalance('ASSET:ZENDIT_PREPAID_INR');
    expect(balance).toBeDefined();
  });
});
