/** API DTOs, mirroring com.anvith.archmorph.api.dto. */

export interface Envelope<T> {
  success: boolean;
  message: string;
  data?: T;
  errorCode?: string;
  hint?: string;
  timestamp: string;
  requestId?: string;
}

export type ProjectStatus =
  | 'QUEUED'
  | 'ANALYZING'
  | 'PLANNING'
  | 'READY_FOR_REVIEW'
  | 'TRANSFORMING'
  | 'VALIDATING'
  | 'COMPLETED'
  | 'FAILED';

export type JobStatus = 'QUEUED' | 'ANALYZING' | 'PLANNING' | 'TRANSFORMING' | 'VALIDATING' | 'COMPLETED' | 'FAILED';

export interface ErrorInfo {
  code: string;
  message: string;
  hint?: string | null;
}

export interface Capabilities {
  canEditModules: boolean;
  canTransform: boolean;
  canDownload: boolean;
  hasPlan: boolean;
  hasValidation: boolean;
  manualReviewRequired: boolean;
}

export interface Project {
  projectId: string;
  name: string;
  status: ProjectStatus;
  createdAt: string;
  expiresAt: string;
  archiveBytes: number;
  capabilities: Capabilities;
  failure?: ErrorInfo | null;
  latestJobId?: string | null;
  latestJobStatus?: JobStatus | null;
  warnings: string[];
}

export interface JobEvent {
  timestamp: string;
  event: string;
  detail: string;
}

export interface Job {
  jobId: string;
  projectId: string;
  type: 'ANALYZE' | 'TRANSFORM' | 'VALIDATE';
  status: JobStatus;
  createdAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  error?: ErrorInfo | null;
  events: JobEvent[];
}

export interface CreatedProject {
  projectId: string;
  jobId: string;
  status: string;
}

export interface Counts {
  javaFiles: number;
  testFiles: number;
  classes: number;
  controllers: number;
  services: number;
  repositories: number;
  entities: number;
  dtos: number;
  configurations: number;
  components: number;
  unknown: number;
  dependencies: number;
  cycles: number;
  layerViolations: number;
  businessModules: number;
}

export interface Classification {
  qualifiedName: string;
  className: string;
  type: string;
  confidence: number;
  evidence: string[];
  module?: string | null;
  file?: string | null;
}

export interface Violation {
  source: string;
  sourceClassName: string;
  sourceType: string;
  target: string;
  targetClassName: string;
  targetType: string;
  dependencyType: string;
  file?: string | null;
  line: number;
  severity: string;
  message: string;
  rationale?: string | null;
}

export interface Cycle {
  cycleId: string;
  nodes: string[];
  classNames: string[];
  path: string[];
  edgeCount: number;
  dependencyTypes: string[];
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  recommendation: string;
}

export interface Analysis {
  project: {
    buildTool: string;
    groupId?: string | null;
    artifactId?: string | null;
    springBoot: boolean;
    multiModule: boolean;
    notes: string[];
  };
  counts: Counts;
  indicators: { architectureHealth: number; moduleConfidence: number; transformationReadiness: number; note: string };
  architecture: { packageStyle: string; averageInstability: number; warnings: string[]; recommendations: string[] };
  classes: Classification[];
  violations: Violation[];
  cycles: Cycle[];
  metrics: { qualifiedName: string; className: string; type: string; afferentCoupling: number; efferentCoupling: number; instability: number }[];
  parseProblems: { file: string; line: number; message: string }[];
  resolution: Record<string, number>;
}

export interface GraphNode {
  id: string;
  className: string;
  packageName: string;
  componentType: string;
  module?: string | null;
  category?: string | null;
  confidence: number;
  afferentCoupling: number;
  efferentCoupling: number;
  inCycle: boolean;
  file?: string | null;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  type: string;
  occurrences: number;
  confidence: number;
  crossModule: boolean;
  violation: boolean;
  inCycle: boolean;
  location?: string | null;
}

export interface Graph {
  nodes: GraphNode[];
  edges: GraphEdge[];
  totalNodes: number;
  totalEdges: number;
  truncated: boolean;
}

export interface ArchitectureView {
  current: {
    packageStyle: string;
    layers: { name: string; componentType: string; count: number; classes: string[] }[];
    packages: Record<string, number>;
  };
  proposed: {
    strategy: string;
    basePackage: string;
    layout: string[];
    modules: { name: string; confidence: number; classCount: number; folders: Record<string, string[]> }[];
    shared: Record<string, string[]>;
    application: string[];
  };
  notes: string[];
}

export interface ModuleClass {
  qualifiedName: string;
  className: string;
  componentType: string;
  confidence: number;
  origin: 'AUTOMATIC' | 'USER';
  locked: boolean;
  excluded: boolean;
  reasons: string[];
}

export interface Module {
  name: string;
  category: string;
  confidence: number;
  cohesion: number;
  externalCoupling: number;
  classCount: number;
  internalDependencies: number;
  externalDependencies: number;
  dependenciesOnModules: Record<string, number>;
  evidence: string[];
  warnings: string[];
  classes: ModuleClass[];
}

export type ModuleEditType =
  | 'RENAME_MODULE'
  | 'MERGE_MODULES'
  | 'SPLIT_MODULE'
  | 'MOVE_CLASS'
  | 'MOVE_TO_SHARED'
  | 'EXCLUDE_CLASS'
  | 'INCLUDE_CLASS'
  | 'LOCK_CLASS'
  | 'UNLOCK_CLASS';

export interface ModuleEdit {
  type: ModuleEditType;
  module?: string | null;
  newName?: string | null;
  target?: string | null;
  sources?: string[] | null;
  className?: string | null;
  classes?: string[] | null;
}

export type SuggestionKind = 'MOVE_CLASS' | 'UNIDIRECTIONAL_RELATIONSHIP' | 'SPLIT_FACADE' | 'INVERT_DEPENDENCY';

export interface BoundarySuggestion {
  id: string;
  kind: SuggestionKind;
  from: string;
  to: string;
  subject?: string | null;
  title: string;
  rationale: string;
  dependencyCount: number;
  steps: string[];
  evidence: string[];
  edit?: ModuleEdit | null;
}

export interface Modules {
  suggestion: Module[];
  decisions: ModuleEdit[];
  finalModules: Module[];
  warnings: string[];
  note: string;
  cycles: string[][];
  boundarySuggestions: BoundarySuggestion[];
}

export interface PlanEntry {
  id: string;
  scope: 'MAIN' | 'TEST';
  className: string;
  sourcePath: string;
  targetPath: string;
  sourcePackage: string;
  targetPackage: string;
  module?: string | null;
  folder?: string | null;
  actions: string[];
  safety: 'SAFE' | 'SAFE_WITH_WARNING' | 'MANUAL_REVIEW' | 'UNSUPPORTED';
  risk: 'LOW' | 'MEDIUM' | 'HIGH';
  confidence: number;
  rewrites: string[];
  reasons: string[];
  classes: { source: string; target: string; nested: boolean }[];
}

export type TargetStrategy = 'MODULAR_MONOLITH' | 'MODULAR_BY_DOMAIN';

export interface Plan {
  strategy: TargetStrategy;
  basePackage: string;
  fingerprint: string;
  summary: {
    files: number;
    moved: number;
    kept: number;
    excluded: number;
    manualReview: number;
    unsupported: number;
    safe: number;
    safeWithWarning: number;
    conflicts: number;
    rewrites: number;
  };
  entries: PlanEntry[];
  conflicts: { type: string; target: string; sources: string[]; resolution: string }[];
  warnings: string[];
  resourceFindings: { file: string; line: number; reference: string; snippet: string }[];
  layout: string[];
  classMap: Record<string, string>;
  modulithVerification: boolean;
  generatedFiles: string[];
}

export interface FileChange {
  entryId: string;
  sourcePath: string;
  targetPath: string;
  changed: boolean;
  packageChanged: boolean;
  importChanges: string[];
  qualifiedRewrites: number;
  linesAdded: number;
  linesRemoved: number;
  warnings: string[];
}

export interface DryRun {
  plan: Plan;
  files: FileChange[];
  warnings: string[];
  durationMillis: number;
}

export interface Diff {
  entryId: string;
  className: string;
  sourcePath: string;
  targetPath: string;
  before: string;
  after: string;
  unified: string;
  changed: boolean;
  truncated: boolean;
  importChanges: string[];
  qualifiedRewrites: number;
}

export type LevelStatus = 'PASS' | 'WARN' | 'FAIL' | 'SKIPPED';

export interface ValidationIssue {
  severity: 'ERROR' | 'WARNING';
  file?: string | null;
  line: number;
  message: string;
  probableCause?: string | null;
}

export interface ValidationLevel {
  level: string;
  label: string;
  status: LevelStatus;
  summary: string;
  issueCount: number;
  issues: ValidationIssue[];
  durationMillis: number;
}

export interface Validation {
  status: LevelStatus;
  completedAt: string;
  levels: ValidationLevel[];
  build?: {
    command: string[];
    exitCode: number;
    stdout: string;
    stderr: string;
    durationMillis: number;
    timedOut: boolean;
    truncated: boolean;
  } | null;
}
