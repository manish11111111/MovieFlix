# 📹 How to Add Video for ExoPlayer

## Location

Place your video file here:

```
app/src/main/res/raw/my_movie.mp4
```

## Requirements

- **Format**: MP4 (recommended), 3GP, or WebM
- **Filename**: `my_movie.mp4` (lowercase, no spaces)
- **Size**: Keep under 10MB for best APK size

## Steps

1. Copy your MP4 video file
2. Rename it to `my_movie.mp4`
3. Place it in `app/src/main/res/raw/` folder
4. Rebuild the app: `./gradlew clean assembleDebug`

## Notes

- The raw folder already exists at `app/src/main/res/raw/`
- If video is missing, ExoPlayer will show an error with instructions
- The "Play Movie" option works with online streaming (doesn't need this file)
- Raw resources are included in the APK, so keep file sizes reasonable

## Testing

- **Without video file**: Shows fallback error screen
- **With video file**: Plays successfully in landscape mode

