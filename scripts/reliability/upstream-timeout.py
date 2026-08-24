"""Safely prove bounded upstream timeout handling against a loopback-only stalled server."""

import asyncio

import httpx
from rxrelay_ingestion.clients import RetryingHttpClient, UpstreamError


async def stalled(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    await reader.read(1024)
    await asyncio.sleep(1)
    writer.close()
    await writer.wait_closed()


async def main() -> int:
    server = await asyncio.start_server(stalled, "127.0.0.1", 0)
    port = server.sockets[0].getsockname()[1]
    try:
        async with httpx.AsyncClient(timeout=0.05) as client:
            resilient = RetryingHttpClient(
                client, attempts=2, base_delay=0.01, max_delay=0.01
            )
            try:
                await resilient.get_json(f"http://127.0.0.1:{port}/stalled", {})
            except UpstreamError as exc:
                print(
                    f"Bounded timeout recovered as UpstreamError: {type(exc.__cause__).__name__}"
                )
                return 0
        print("Expected an UpstreamError but the request unexpectedly succeeded")
        return 1
    finally:
        server.close()
        await server.wait_closed()


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
