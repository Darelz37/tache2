export const STATUS_LABEL: Record<string, string> = {
  draft: 'brouillon', scheduled: 'planifiée', published: 'publiée', unpublished: 'dépubliée',
};

export function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

export function apiMessage(e: any, fallback = 'Une erreur est survenue.'): string {
  return e?.error?.error?.message ?? fallback;
}
