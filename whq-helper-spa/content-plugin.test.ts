import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { resolveContentDir } from './content-plugin';

describe('resolveContentDir', () => {
  it('defaults to whq-content next to the repository', () => {
    expect(resolveContentDir(undefined, '/repo/whq-helper-spa')).toBe(resolve('/repo/../whq-content'));
    expect(resolveContentDir('  ', '/repo/whq-helper-spa')).toBe(resolve('/repo/../whq-content'));
  });

  it('takes WHQ_CONTENT_HOME, relative paths from the current directory', () => {
    expect(resolveContentDir('/packages/my-content', '/repo/whq-helper-spa')).toBe('/packages/my-content');
    expect(resolveContentDir('../shared/sample', '/repo/whq-helper-spa')).toBe(resolve(process.cwd(), '../shared/sample'));
  });
});
