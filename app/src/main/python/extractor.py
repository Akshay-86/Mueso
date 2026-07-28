import yt_dlp
import traceback
import sys
import os

# Prevent native _posixsubprocess crashes in Chaquopy on Android when yt-dlp checks for external tools
try:
    import subprocess
    def _disabled_popen(*args, **kwargs):
        raise OSError("Subprocesses are disabled on Android")
    subprocess.Popen = _disabled_popen
except Exception:
    pass

def search_tracks(query, max_results=20):
    print(f"[EXTRACTOR] search_tracks called with query='{query}', max_results={max_results}", flush=True)
    if not query or not query.strip():
        print("[EXTRACTOR] search_tracks: query is empty, returning empty list", flush=True)
        return []
    
    ydl_opts = {
        'extract_flat': True,
        'skip_download': True,
        'quiet': False,
        'no_warnings': False,
        'nocheckcertificate': True,
        'external_downloader': None,
        'noplaylist': True,
    }

    search_target = query if query.startswith("http") else f"ytsearch{max_results}:{query}"
    print(f"[EXTRACTOR] search_tracks: search_target='{search_target}'", flush=True)

    try:
        print("[EXTRACTOR] search_tracks: initializing YoutubeDL instance...", flush=True)
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            print("[EXTRACTOR] search_tracks: extracting info from yt-dlp...", flush=True)
            info = ydl.extract_info(search_target, download=False)
            print(f"[EXTRACTOR] search_tracks: extract_info completed. info type={type(info)}", flush=True)
            entries = info.get('entries', []) if info and 'entries' in info else ([info] if info else [])
            print(f"[EXTRACTOR] search_tracks: found {len(entries)} entries", flush=True)
            results = []
            for idx, entry in enumerate(entries):
                if not entry:
                    print(f"[EXTRACTOR] search_tracks: entry {idx} is None, skipping", flush=True)
                    continue
                video_id = entry.get("id") or entry.get("url")
                print(f"[EXTRACTOR] search_tracks: entry {idx} video_id='{video_id}', title='{entry.get('title')}'", flush=True)
                if not video_id:
                    continue
                results.append({
                    "videoId": str(video_id),
                    "title": entry.get("title", "Unknown Title"),
                    "artist": entry.get("uploader") or entry.get("channel") or "Unknown Artist",
                    "duration": int(entry.get("duration", 0) or 0),
                    "thumbnail": f"https://i.ytimg.com/vi/{video_id}/hq720.jpg"
                })
            print(f"[EXTRACTOR] search_tracks: returning {len(results)} structured results", flush=True)
            return results
    except Exception as e:
        print(f"[EXTRACTOR] ERROR in search_tracks: {e}", flush=True)
        traceback.print_exc()
        return []

def search_and_extract(query):
    print(f"[EXTRACTOR] search_and_extract called with query='{query}'", flush=True)
    ydl_opts = {
        'format': 'bestaudio[ext=m4a]/bestaudio/best',
        'skip_download': True,
        'quiet': False,
        'no_warnings': False,
        'extract_flat': False,
        'nocheckcertificate': True,
        'ignoreerrors': True,
        'external_downloader': None,
        'noplaylist': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['android_vr', 'web_embedded', 'android', 'web']
            }
        }
    }

    search_target = query if query.startswith("http") else f"https://www.youtube.com/watch?v={query}" if (len(query) == 11 and not " " in query) else f"ytsearch1:{query}"
    print(f"[EXTRACTOR] search_and_extract: search_target='{search_target}'", flush=True)

    try:
        print("[EXTRACTOR] search_and_extract: initializing YoutubeDL instance...", flush=True)
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            print("[EXTRACTOR] search_and_extract: extracting info from yt-dlp...", flush=True)
            info = ydl.extract_info(search_target, download=False)
            print(f"[EXTRACTOR] search_and_extract: extract_info completed. info is None? {info is None}", flush=True)
            if not info:
                print("[EXTRACTOR] search_and_extract: ERROR - info is None", flush=True)
                return {"error": "No info returned"}
            
            if 'entries' in info and info['entries']:
                print("[EXTRACTOR] search_and_extract: using first item in entries array", flush=True)
                entry = info['entries'][0]
            else:
                entry = info

            if not entry:
                print("[EXTRACTOR] search_and_extract: ERROR - entry is None", flush=True)
                return {"error": "No entry found"}

            stream_url = None
            formats = entry.get('formats', [])
            print(f"[EXTRACTOR] search_and_extract: total formats found = {len(formats)}", flush=True)
            
            # 1. First priority: Audio-only streams with direct URLs (vcodec == 'none' and acodec != 'none')
            audio_formats = [f for f in formats if f.get('vcodec') == 'none' and f.get('acodec') != 'none' and f.get('url')]
            print(f"[EXTRACTOR] search_and_extract: audio_formats count = {len(audio_formats)}", flush=True)
            if audio_formats:
                best_audio = max(audio_formats, key=lambda f: f.get('tbr', 0) or f.get('abr', 0) or 0)
                stream_url = best_audio.get('url')
                print(f"[EXTRACTOR] search_and_extract: selected best_audio tbr={best_audio.get('tbr')}, abr={best_audio.get('abr')}, format_id={best_audio.get('format_id')}, acodec={best_audio.get('acodec')}", flush=True)

            # 2. Second priority: Any video+audio format with a direct URL (acodec != 'none')
            if not stream_url:
                print("[EXTRACTOR] search_and_extract: no audio_formats url, trying any_url_formats...", flush=True)
                any_url_formats = [f for f in formats if f.get('acodec') != 'none' and f.get('url')]
                print(f"[EXTRACTOR] search_and_extract: any_url_formats count = {len(any_url_formats)}", flush=True)
                if any_url_formats:
                    stream_url = any_url_formats[0].get('url')

            # 3. Fallback to entry.get("url")
            if not stream_url:
                print("[EXTRACTOR] search_and_extract: trying entry.get('url')...", flush=True)
                stream_url = entry.get('url')

            print(f"[EXTRACTOR] search_and_extract: final stream_url found? {bool(stream_url)}", flush=True)
            if stream_url:
                print(f"[EXTRACTOR] search_and_extract: stream_url prefix = '{stream_url[:80]}...'", flush=True)
            else:
                print("[EXTRACTOR] search_and_extract: ERROR - stream_url is empty!", flush=True)

            video_id = entry.get("id") or query
            highres_thumb = f"https://i.ytimg.com/vi/{video_id}/hq720.jpg"

            return {
                "title": entry.get("title", "Unknown Title"),
                "artist": entry.get("uploader", "Unknown Artist"),
                "stream_url": stream_url or "",
                "thumbnail": highres_thumb,
                "duration": int(entry.get("duration", 0) or 0)
            }
    except Exception as e:
        print(f"[EXTRACTOR] CRITICAL EXCEPTION in search_and_extract: {e}", flush=True)
        traceback.print_exc()
        return {"error": str(e)}

def extract_stream(video_id):
    print(f"[EXTRACTOR] extract_stream called for video_id='{video_id}'", flush=True)
    if not video_id:
        print("[EXTRACTOR] extract_stream: video_id is empty", flush=True)
        return ""
    url = f"https://www.youtube.com/watch?v={video_id}"
    print(f"[EXTRACTOR] extract_stream: calling search_and_extract with url='{url}'", flush=True)
    res = search_and_extract(url)
    print(f"[EXTRACTOR] extract_stream: search_and_extract returned type={type(res)}", flush=True)
    if isinstance(res, dict):
        url_val = res.get("stream_url") or ""
        print(f"[EXTRACTOR] extract_stream: stream_url val len={len(url_val)}", flush=True)
        return url_val
    print("[EXTRACTOR] extract_stream: res was not a dict", flush=True)
    return ""

def embed_metadata(file_path, title, artist, album="Mueso Downloads", artwork_url=None):
    print(f"[EXTRACTOR] embed_metadata called for file='{file_path}', title='{title}', artist='{artist}'", flush=True)
    try:
        import urllib.request
        from mutagen.mp4 import MP4, MP4Cover
        from mutagen.id3 import ID3, TIT2, TPE1, TALB, APIC, ID3NoHeaderError

        cover_data = None
        if artwork_url:
            try:
                if "i.ytimg.com/vi/" in artwork_url:
                    video_id = artwork_url.split("/vi/")[1].split("/")[0]
                    full_urls = [
                        f"https://i.ytimg.com/vi/{video_id}/maxresdefault.jpg",
                        f"https://i.ytimg.com/vi/{video_id}/hq720.jpg",
                        artwork_url
                    ]
                else:
                    full_urls = [artwork_url]

                for art_url in full_urls:
                    try:
                        print(f"[EXTRACTOR] Downloading artwork for embedding: {art_url}", flush=True)
                        req = urllib.request.Request(art_url, headers={'User-Agent': 'Mozilla/5.0'})
                        with urllib.request.urlopen(req, timeout=8) as resp:
                            cover_data = resp.read()
                            if len(cover_data) > 1000:
                                print(f"[EXTRACTOR] Downloaded {len(cover_data)} bytes of full uncropped artwork image", flush=True)
                                break
                    except Exception:
                        continue
            except Exception as img_err:
                print(f"[EXTRACTOR] Could not download cover image: {img_err}", flush=True)

        if file_path.endswith(".m4a") or file_path.endswith(".mp4"):
            try:
                audio = MP4(file_path)
            except Exception:
                print(f"[EXTRACTOR] Mutagen MP4 parse error, creating new tags...", flush=True)
                audio = MP4(file_path)

            audio["\xa9nam"] = [title]
            audio["\xa9ART"] = [artist]
            audio["\xa9alb"] = [album]
            if cover_data:
                image_format = MP4Cover.FORMAT_JPEG if (b"\xff\xd8" in cover_data[:10]) else MP4Cover.FORMAT_PNG
                audio["covr"] = [MP4Cover(cover_data, imageformat=image_format)]
            audio.save()
            print(f"[EXTRACTOR] Successfully embedded MP4 metadata into {file_path}", flush=True)
            return True
        else:
            try:
                audio = ID3(file_path)
            except ID3NoHeaderError:
                audio = ID3()

            audio.add(TIT2(encoding=3, text=title))
            audio.add(TPE1(encoding=3, text=artist))
            audio.add(TALB(encoding=3, text=album))
            if cover_data:
                mime = "image/jpeg" if (b"\xff\xd8" in cover_data[:10]) else "image/png"
                audio.add(APIC(
                    encoding=3,
                    mime=mime,
                    type=3,
                    desc='Cover',
                    data=cover_data
                ))
            audio.save(file_path)
            print(f"[EXTRACTOR] Successfully embedded ID3 metadata into {file_path}", flush=True)
            return True
    except Exception as e:
        print(f"[EXTRACTOR] Error in embed_metadata: {e}", flush=True)
        traceback.print_exc()
        return False
