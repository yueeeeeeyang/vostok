import { describe, it, expect } from 'vitest';
import { RestDefaultAdapter } from '@pkg/adapters/default-adapter';

describe('adapter contract', () => {
  const adapter = new RestDefaultAdapter();

  it('normalizes format A', () => {
    const out = adapter.normalizeSuccess<{ id: string }>({ code: 0, message: 'ok', data: { id: 'u1' } });
    expect(out.ok).toBe(true);
    expect(out.data.id).toBe('u1');
  });

  it('normalizes format B', () => {
    const out = adapter.normalizeSuccess<{ id: string }>({ success: true, result: { id: 'u2' } });
    expect(out.ok).toBe(true);
    expect(out.data.id).toBe('u2');
  });

  it('normalizes format C', () => {
    const out = adapter.normalizeSuccess<{ id: string }>({ payload: { id: 'u3' } });
    expect(out.data.id).toBe('u3');
  });
});
