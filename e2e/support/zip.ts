import zlib from 'node:zlib'

/** Reads a ZIP file (stored or deflated entries) into file name -> content, via its central directory. */
export function unzip(zip: Buffer): Map<string, Buffer> {
  let eocd = zip.length - 22
  while (eocd >= 0 && zip.readUInt32LE(eocd) !== 0x06054b50) eocd--
  if (eocd < 0) throw new Error('Not a ZIP file')
  const count = zip.readUInt16LE(eocd + 10)
  let offset = zip.readUInt32LE(eocd + 16)
  const files = new Map<string, Buffer>()
  for (let i = 0; i < count; i++) {
    if (zip.readUInt32LE(offset) !== 0x02014b50) throw new Error('Broken central directory')
    const method = zip.readUInt16LE(offset + 10)
    const size = zip.readUInt32LE(offset + 20)
    const nameLength = zip.readUInt16LE(offset + 28)
    const extraLength = zip.readUInt16LE(offset + 30)
    const commentLength = zip.readUInt16LE(offset + 32)
    const local = zip.readUInt32LE(offset + 42)
    const name = zip.subarray(offset + 46, offset + 46 + nameLength).toString('utf8')
    const start = local + 30 + zip.readUInt16LE(local + 26) + zip.readUInt16LE(local + 28)
    const data = zip.subarray(start, start + size)
    files.set(name, method === 8 ? zlib.inflateRawSync(data) : Buffer.from(data))
    offset += 46 + nameLength + extraLength + commentLength
  }
  return files
}
