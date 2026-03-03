import { describe, it, expect } from 'vitest';
import { RestDefaultAdapter } from '@pkg/adapters/default-adapter';

describe('error mapping contract', () => {
  const adapter = new RestDefaultAdapter();

  it('maps auth errors', () => {
    const out = adapter.normalizeError({ status: 401, message: 'unauthorized' });
    expect(out.kind).toBe('auth');
  });

  it('maps rate limit errors', () => {
    const out = adapter.normalizeError({ status: 429, message: 'too many requests' });
    expect(out.kind).toBe('rate_limit');
  });

  it('maps graphql auth errors', () => {
    const out = adapter.normalizeError({
      errors: [{ message: 'not logged in', extensions: { code: 'UNAUTHENTICATED' } }]
    });
    expect(out.kind).toBe('auth');
    expect(out.code).toBe('UNAUTHENTICATED');
  });

  it('maps graphql business errors', () => {
    const out = adapter.normalizeError({
      errors: [{ message: 'invalid input', extensions: { code: 'BAD_USER_INPUT' } }]
    });
    expect(out.kind).toBe('biz');
    expect(out.code).toBe('BAD_USER_INPUT');
  });
});
