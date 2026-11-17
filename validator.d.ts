export interface LegacyOptions {
  includeWarnings?: boolean;
  resourceDir?: string;
  xmlSnippetMaxLength?: number;
  parsedSchematronMap?: SchematronMap;
}
export interface SchematronMap {
  namespaceMap: Record<string, string>;
  patternRuleMap: Record<string, string[]>;
  ruleAssertionMap: Record<string, unknown>;
}
export interface LegacyFinding {
  type: 'error' | 'warning';
  test: string;
  simplifiedTest: string | null;
  description: string;
  line?: number | null;
  path?: string;
  patternId?: string;
  ruleId?: string;
  assertionId?: string;
  context?: string | null;
  xml?: string | null;
  errorMessage?: string;
}
export interface LegacyResult {
  errorCount: number;
  warningCount: number;
  ignoredCount: number;
  errors: LegacyFinding[];
  warnings: LegacyFinding[];
  ignored: LegacyFinding[];
}
export function validate(xml: string, schematron: string | null, options?: LegacyOptions): LegacyResult;
export function validateAsync(xml: string, schematron: string | null, options?: LegacyOptions): Promise<LegacyResult>;
export function validateFileListAsync(xml: string[], schematron: string | null, options?: LegacyOptions): Promise<Array<LegacyResult & {fileIndex: number}>>;
export function validateFileObjectAsync(xml: Record<string, string>, schematron: string | null, options?: LegacyOptions): Promise<Array<LegacyResult & {fileInfo: string}>>;
export function parseSchematron(document: unknown, options?: LegacyOptions): SchematronMap;
export function clearCache(): void;
export function clearCacheAsync(): void;
export interface Profile {
  id: string;
  name: string;
  release: string;
  schemaRelease: string;
  limitations: string[];
  artifactChecksums: Record<string, string>;
  generationCoverage?: Record<string, number>;
  generationReport?: Record<string, unknown>;
  terminologySnapshots?: Array<Record<string, unknown>>;
  supplementalChecks?: Array<{ ruleId: string; method: string; scope: string }>;
}
export interface Finding {
  messageTruncated?: boolean;
  severity: 'error' | 'warning';
  stage: 'xml' | 'xsd' | 'schematron' | 'coverage' | 'engine';
  message: string;
  line?: number;
  column?: number;
  xpath?: string;
  ruleId?: string;
  patternId?: string;
  context?: string;
  test?: string;
}
export interface ValidationReport {
  applicationVersion: string;
  requestId: string;
  status: 'valid' | 'invalid' | 'incomplete';
  validationComplete: boolean;
  executionComplete: boolean;
  truncated: boolean;
  options: {includeWarnings: boolean};
  summary: {errors: number; warnings: number; findings: number; firedRules: number};
  findings: Finding[];
  stages: Array<{name: string; status: string; firedRules?: number}>;
  profile: Profile;
  engine: Record<string, string>;
  sources: Array<{id: string; release: string; url: string; sha256: string}>;
  uploadQueueWaitMs?: number;
  durationMs: number;
  validatedAt: string;
}
export interface Client {
  validate(xml: string, options?: {profile?: string; includeWarnings?: boolean}): Promise<ValidationReport>;
  profiles(): Promise<{profiles: Profile[]; sources: ValidationReport['sources']; limits?: {maxXmlBytes: number; maxRequestBytes: number; uploadMemoryBudgetBytes: number; maxConcurrentUploads: number; requestCapacity: number; uploadQueueWaitSeconds: number}}>;
  version(): Promise<{version: string; engine: string}>;
}
export function createClient(options?: {baseUrl?: string; token?: string; timeoutMs?: number}): Client;
