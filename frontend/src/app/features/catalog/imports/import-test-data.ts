import {
  ImportErrorPage,
  ImportPreviewResponse,
  ImportResponse,
} from '../../../api/generated/models';

export const JOB: ImportResponse = {
  id: '0193a0b4-0000-7000-8000-0000000000c3',
  fileName: 'prices.csv',
  status: 'UPLOADED',
  rowsTotal: 3,
  rowsProcessed: 0,
  rowsCreated: 0,
  rowsUpdated: 0,
  rowsUnchanged: 0,
  rowsFailed: 0,
  productsArchived: 0,
  completeCatalog: false,
  createdAt: '2026-11-09T10:00:00Z',
};

export const PREVIEW: ImportPreviewResponse = {
  headers: ['Item code', 'Product name', 'Rate', 'Qty'],
  rows: [
    { rowNumber: 2, values: ['PVC-20', 'PVC pipe 20 mm', '185.50', '420'] },
    { rowNumber: 3, values: ['PVC-25', 'PVC pipe 25 mm', '240', '310'] },
  ],
  rowCount: 3,
  suggestedMapping: { sku: 0, name: 1, price: 2, stockQty: 3 },
};

export const ERRORS: ImportErrorPage = {
  items: [{ rowNumber: 4, field: 'price', message: 'Price "12.5O" is not a number like 12.50.' }],
  page: 0,
  size: 50,
  totalItems: 1,
  totalPages: 1,
};
