import type { Rule } from '../types';

export function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export function joinCsv(values: string[]): string {
  return values.join(', ');
}

export function formatRuleLinks(links: Record<string, { text: string; parameter: string }>): string {
  return Object.entries(links)
    .map(([key, value]) => {
      const text = value?.text ?? '';
      const parameters = (value as { parameters?: string[] } | undefined)?.parameters ?? [];
      const parameter = value?.parameter?.trim?.() ?? '';
      const suffix = parameters.length > 1 ? ` [${parameters.join(' | ')}]` : parameter ? ` [${parameter}]` : '';
      return `${text} (${key})${suffix}`;
    })
    .join('\n');
}

export function buildSpecialRuleText(name: string, parameters: string[], parameterFormat = ''): string {
  const normalizedName = name?.trim?.() ?? '';
  const normalizedParameters = parameters.map((value) => value?.trim?.() ?? '');
  const normalizedFormat = parameterFormat?.trim?.() ?? '';
  if (normalizedParameters.every((value) => !value)) {
    return normalizedName;
  }
  if (normalizedFormat) {
    let rendered = normalizedFormat.replaceAll('{name}', normalizedName);
    rendered = rendered.replaceAll('{param}', normalizedParameters[0] ?? '');
    normalizedParameters.forEach((value, index) => {
      rendered = rendered.replaceAll(`{${index}}`, value);
    });
    return rendered.trim();
  }
  return `${normalizedName} ${normalizedParameters[0] ?? ''}`.trim();
}

export function inferRuleParameters(name: string, text: string, parameterFormat = ''): string[] {
  const normalizedName = name?.trim?.() ?? '';
  const normalizedText = text?.trim?.() ?? '';
  const normalizedFormat = parameterFormat?.trim?.() ?? '';
  if (!normalizedName || !normalizedText) {
    return [];
  }
  if (normalizedFormat) {
    const source = normalizedFormat.replaceAll('{name}', normalizedName);
    const captureRegex = /(\{(?:\d+|param)\})/g;
    const parts = source.split(captureRegex).filter(Boolean);
    const placeholders = parts.filter((part) => /^\{(?:\d+|param)\}$/.test(part));
    if (placeholders.length > 0) {
      const pattern = parts
        .map((part) => (/^\{(?:\d+|param)\}$/.test(part) ? '(.*?)' : part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
        .join('');
      const match = new RegExp(`^${pattern}$`, 'i').exec(normalizedText);
      if (match) {
        const values: string[] = [];
        placeholders.forEach((placeholder, index) => {
          const targetIndex = placeholder === '{param}' ? 0 : Number.parseInt(placeholder.slice(1, -1), 10);
          values[targetIndex] = match[index + 1]?.trim?.() ?? '';
        });
        return values;
      }
    }
  }
  if (!normalizedText.toLowerCase().startsWith(normalizedName.toLowerCase())) {
    return [];
  }
  return [normalizedText.slice(normalizedName.length).trim()];
}

export function ruleParameterLabels(rule?: Pick<Rule, 'parameterName' | 'parameterNames'> | undefined): string[] {
  if (!rule) {
    return [];
  }
  if (rule.parameterNames?.length) {
    return rule.parameterNames;
  }
  return rule.parameterName ? [rule.parameterName] : [];
}

export function treasureUsersToFlags(users: string): Record<'B' | 'D' | 'E' | 'W', boolean> {
  const normalized = users.trim().toUpperCase();
  return {
    B: normalized.includes('B'),
    D: normalized.includes('D'),
    E: normalized.includes('E'),
    W: normalized.includes('W')
  };
}

export function treasureUsersFromFlags(flags: Record<'B' | 'D' | 'E' | 'W', boolean>): string {
  return `${flags.B ? 'B' : ''}${flags.D ? 'D' : ''}${flags.E ? 'E' : ''}${flags.W ? 'W' : ''}`;
}

export function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '');
    reader.onerror = () => reject(reader.error ?? new Error('Unable to read file'));
    reader.readAsDataURL(file);
  });
}
