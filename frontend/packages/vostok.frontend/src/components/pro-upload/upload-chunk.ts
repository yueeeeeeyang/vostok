export interface ChunkPayload {
  fileName: string;
  chunkIndex: number;
  totalChunks: number;
  bytes: Uint8Array;
}

export function splitFile(file: File, chunkSize = 1024 * 1024): ChunkPayload[] {
  const totalChunks = Math.ceil(file.size / chunkSize);
  const chunks: ChunkPayload[] = [];
  for (let i = 0; i < totalChunks; i += 1) {
    const start = i * chunkSize;
    const end = Math.min(start + chunkSize, file.size);
    chunks.push({
      fileName: file.name,
      chunkIndex: i,
      totalChunks,
      bytes: new Uint8Array()
    });
    void file.slice(start, end);
  }
  return chunks;
}
