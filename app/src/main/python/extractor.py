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
                'player_client': ['android', 'ios']
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
            raw_formats = entry.get('formats', [])
            print(f"[EXTRACTOR] search_and_extract: total formats found = {len(raw_formats)}", flush=True)
            
            # Filter out storyboards, formats without direct url, or forbidden android_vr formats
            formats = [
                f for f in raw_formats 
                if f.get('url') 
                and not str(f.get('format_id', '')).startswith('sb')
                and 'c=ANDROID_VR' not in str(f.get('url', ''))
            ]

            # 1. First priority: Audio-only streams with direct URLs (vcodec == 'none' and acodec != 'none')
            audio_formats = [f for f in formats if f.get('vcodec') == 'none' and f.get('acodec') != 'none']
            print(f"[EXTRACTOR] search_and_extract: audio_formats count = {len(audio_formats)}", flush=True)
            if audio_formats:
                best_audio = max(audio_formats, key=lambda f: f.get('tbr', 0) or f.get('abr', 0) or 0)
                stream_url = best_audio.get('url')
                print(f"[EXTRACTOR] search_and_extract: selected best_audio tbr={best_audio.get('tbr')}, abr={best_audio.get('abr')}, format_id={best_audio.get('format_id')}, acodec={best_audio.get('acodec')}", flush=True)

            # 2. Second priority: Any video+audio format with a direct URL (acodec != 'none')
            if not stream_url:
                print("[EXTRACTOR] search_and_extract: no audio_formats url, trying any_url_formats...", flush=True)
                any_url_formats = [f for f in formats if f.get('acodec') != 'none']
                print(f"[EXTRACTOR] search_and_extract: any_url_formats count = {len(any_url_formats)}", flush=True)
                if any_url_formats:
                    stream_url = any_url_formats[0].get('url')

            # 3. Fallback to entry.get("url")
            if not stream_url:
                print("[EXTRACTOR] search_and_extract: trying entry.get('url')...", flush=True)
                entry_url = entry.get('url')
                if entry_url and 'c=ANDROID_VR' not in entry_url:
                    stream_url = entry_url

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

def extract_available_resolutions(video_id):
    if not video_id:
        return []
    clean_id = str(video_id).strip()
    if clean_id.startswith("online:"):
        clean_id = clean_id[7:].strip()
    url = clean_id if clean_id.startswith("http") else f"https://www.youtube.com/watch?v={clean_id}"
    print(f"[EXTRACTOR] extract_available_resolutions for url='{url}'", flush=True)

    ydl_opts = {
        'skip_download': True,
        'quiet': False,
        'no_warnings': False,
        'extract_flat': False,
        'nocheckcertificate': True,
        'ignoreerrors': True,
        'noplaylist': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['visionos', 'android', 'web', 'ios']
            }
        }
    }
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            if not info:
                return []
            entry = info['entries'][0] if ('entries' in info and info['entries']) else info
            raw_formats = entry.get('formats', [])
            valid_video_formats = [
                f for f in raw_formats 
                if f.get('url') 
                and not str(f.get('format_id', '')).startswith('sb')
                and 'c=ANDROID_VR' not in str(f.get('url', ''))
                and 'manifest.googlevideo.com' not in str(f.get('url', ''))
                and not str(f.get('protocol', '')).startswith('m3u8')
                and f.get('vcodec') != 'none'
                and (f.get('height') or 0) > 0
            ]
            heights = sorted(list(set([int(f.get('height')) for f in valid_video_formats if f.get('height')])), reverse=True)
            res_labels = []
            for h in heights:
                if h >= 4320:
                    res_labels.append(f"{h}p (8K Ultra HD)")
                elif h >= 2160:
                    res_labels.append(f"{h}p (4K UHD)")
                elif h >= 1440:
                    res_labels.append(f"{h}p (2K QHD)")
                elif h >= 1080:
                    res_labels.append(f"{h}p (Full HD)")
                elif h >= 720:
                    res_labels.append(f"{h}p (HD)")
                elif h >= 480:
                    res_labels.append(f"{h}p (SD)")
                elif h >= 360:
                    res_labels.append(f"{h}p (Medium)")
                elif h >= 240:
                    res_labels.append(f"{h}p (Low)")
                else:
                    res_labels.append(f"{h}p")
            print(f"[EXTRACTOR] extract_available_resolutions found: {res_labels}", flush=True)
            return res_labels
    except Exception as e:
        print(f"[EXTRACTOR] Error in extract_available_resolutions: {e}", flush=True)
        traceback.print_exc()
        return []

def extract_video_stream(video_id, target_resolution="1080p"):
    if not video_id:
        return {"error": "Empty id", "stream_url": "", "audio_url": "", "resolution": "", "available_resolutions": []}
    clean_id = str(video_id).strip()
    if clean_id.startswith("online:"):
        clean_id = clean_id[7:].strip()
    url = clean_id if clean_id.startswith("http") else f"https://www.youtube.com/watch?v={clean_id}"
    print(f"[EXTRACTOR] extract_video_stream for url='{url}', target_res='{target_resolution}'", flush=True)

    ydl_opts = {
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
                'player_client': ['visionos', 'android', 'web', 'ios']
            }
        }
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            if not info:
                return {"error": "No info returned", "stream_url": "", "audio_url": "", "resolution": "", "available_resolutions": []}
            
            entry = info['entries'][0] if ('entries' in info and info['entries']) else info
            raw_formats = entry.get('formats', [])
            
            # Filter to direct HTTPS video formats (no HLS manifests)
            valid_video_formats = [
                f for f in raw_formats 
                if f.get('url') 
                and not str(f.get('format_id', '')).startswith('sb')
                and 'c=ANDROID_VR' not in str(f.get('url', ''))
                and 'manifest.googlevideo.com' not in str(f.get('url', ''))
                and not str(f.get('protocol', '')).startswith('m3u8')
                and f.get('vcodec') != 'none'
                and (f.get('height') or 0) > 0
            ]

            # Find best audio-only stream (for muxing with video-only streams)
            audio_only_formats = [
                f for f in raw_formats
                if f.get('url')
                and f.get('acodec') != 'none'
                and f.get('vcodec') == 'none'
                and 'manifest.googlevideo.com' not in str(f.get('url', ''))
                and not str(f.get('protocol', '')).startswith('m3u8')
            ]
            # Find best audio-only stream (for muxing with video-only streams)
            best_audio = None
            if audio_only_formats:
                # Pick highest bitrate audio — FFmpeg handles any codec
                best_audio = max(audio_only_formats, key=lambda f: (
                    f.get('abr') or f.get('tbr') or 0
                ))

            audio_url = best_audio.get('url', '') if best_audio else ''

            res_str = str(target_resolution).lower()
            target_h = 1080
            if "8k" in res_str or "4320" in res_str or "max" in res_str or "highest" in res_str or "best" in res_str:
                target_h = 8000
            elif "4k" in res_str or "2160" in res_str or "uhd" in res_str:
                target_h = 2160
            elif "1440" in res_str or "2k" in res_str or "qhd" in res_str:
                target_h = 1440
            elif "1080" in res_str or "default" in res_str or "fhd" in res_str:
                target_h = 1080
            elif "720" in res_str or "hd" in res_str:
                target_h = 720
            elif "480" in res_str or "sd" in res_str or "low" in res_str:
                target_h = 480
            elif "360" in res_str:
                target_h = 360
            elif "240" in res_str:
                target_h = 240
            elif "144" in res_str:
                target_h = 144

            # Select best video format at target resolution — FFmpeg handles any codec
            matching = [f for f in valid_video_formats if (f.get('height') or 0) <= target_h]
            if not matching:
                matching = valid_video_formats
            
            selected_format = None
            if matching:
                selected_format = max(matching, key=lambda f: (
                    f.get('height') or 0,
                    f.get('tbr') or 0
                ))

            stream_url = selected_format.get('url') if selected_format else ""
            actual_height = (selected_format.get('height') or 0) if selected_format else 0
            actual_res = f"{actual_height}p" if actual_height > 0 else "Video"
            has_audio = (selected_format.get('acodec') != 'none') if selected_format else False

            avail_heights = sorted(list(set([int(f.get('height')) for f in valid_video_formats if f.get('height')])), reverse=True)
            avail_res = []
            for h in avail_heights:
                if h >= 4320:
                    avail_res.append(f"{h}p (8K Ultra HD)")
                elif h >= 2160:
                    avail_res.append(f"{h}p (4K UHD)")
                elif h >= 1440:
                    avail_res.append(f"{h}p (2K QHD)")
                elif h >= 1080:
                    avail_res.append(f"{h}p (Full HD)")
                elif h >= 720:
                    avail_res.append(f"{h}p (HD)")
                elif h >= 480:
                    avail_res.append(f"{h}p (SD)")
                elif h >= 360:
                    avail_res.append(f"{h}p (Medium)")
                elif h >= 240:
                    avail_res.append(f"{h}p (Low)")
                else:
                    avail_res.append(f"{h}p")

            # If selected format already has audio, no need for separate audio stream
            if has_audio:
                audio_url = ""

            print(f"[EXTRACTOR] extract_video_stream selected actual_res={actual_res}, has_audio={has_audio}, audio_url={'yes' if audio_url else 'no'}, stream_url prefix={stream_url[:60] if stream_url else 'None'}", flush=True)

            return {
                "title": entry.get("title", "Unknown Title"),
                "artist": entry.get("uploader", "Unknown Artist"),
                "stream_url": stream_url or "",
                "audio_url": audio_url or "",
                "resolution": actual_res,
                "available_resolutions": avail_res,
                "duration": int(entry.get("duration", 0) or 0)
            }
    except Exception as e:
        print(f"[EXTRACTOR] ERROR in extract_video_stream: {e}", flush=True)
        traceback.print_exc()
        return {"error": str(e), "stream_url": "", "audio_url": "", "resolution": "", "available_resolutions": []}

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
