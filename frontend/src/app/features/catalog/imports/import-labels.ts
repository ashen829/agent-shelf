import { ImportResponse } from '../../../api/generated/models';

type Status = ImportResponse['status'];

/** What each import status means to the supplier. */
export const STATUS_LABELS: Record<Status, string> = {
  UPLOADED: 'Waiting for you to match columns',
  QUEUED: 'Waiting to start',
  RUNNING: 'Importing',
  COMPLETED: 'Done',
  COMPLETED_WITH_ERRORS: 'Done, some rows have errors',
  FAILED: 'Failed',
};

export function isRunning(status: Status): boolean {
  return status === 'QUEUED' || status === 'RUNNING';
}

export function isFinished(status: Status): boolean {
  return status === 'COMPLETED' || status === 'COMPLETED_WITH_ERRORS' || status === 'FAILED';
}

/** Product fields a file column can fill, in the order the mapping form shows them. */
export const FIELDS = [
  { key: 'sku', label: 'SKU', required: true },
  { key: 'name', label: 'Name', required: true },
  { key: 'price', label: 'Price', required: true },
  { key: 'stockQty', label: 'Stock', required: false },
  { key: 'unit', label: 'Unit', required: false },
  { key: 'category', label: 'Category', required: false },
  { key: 'description', label: 'Description', required: false },
] as const;

export type FieldKey = (typeof FIELDS)[number]['key'];

export const MAX_FILE_BYTES = 10 * 1024 * 1024;

/** The label suppliers know for an API field name, for example "stockQty" -> "Stock". */
export function fieldLabel(field: string | null | undefined): string {
  return FIELDS.find((f) => f.key === field)?.label ?? '—';
}
