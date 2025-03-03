# Film Simulator
<p float="left">
<img src="https://github.com/YahiaAngelo/Film-Simulator/blob/main/imgs/screenshots.jpg?raw=true" alt="screenshots"/>
</p>
 <a href="https://apps.apple.com/us/app/film-simulator/id6736855160?itscg=30200&itsct=apps_box_badge&mttnsubad=6736855160" style="display: inline-block;">
 <img src="https://toolbox.marketingtools.apple.com/api/v2/badges/download-on-the-app-store/black/en-us?releaseDate=1728950400" alt="Download on the App Store" style="width: 200px; height: 82px; vertical-align: middle; object-fit: contain;" />
 </a>
 <a href="https://play.google.com/store/apps/details?id=io.github.yahiaangelo.filmsimulator.android" target="_blank">
  <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" style="width: 200px; height: 82px; vertical-align: middle; object-fit: contain;"/>
</a>


## Overview
FilmSimulator is a cross-platform mobile application developed using Kotlin Multiplatform and Compose UI Multiplatform. It is designed for both Android and iOS platforms. The app allows users to apply different film-like LUTs (Look-Up Tables) to their images, providing a unique aesthetic reminiscent of classic film styles.

## Features
- Apply film-like LUTs to any image.
- Apply image adjustments like brightness, contrast, saturation, and more.
- Simple and intuitive UI built with Compose UI and Material3.
- Cross-platform functionality on both Android and iOS devices.

## Technologies and Libraries
This project leverages the following technologies and libraries:
- **Compose UI**: Used to build the UI components in a declarative style across both platforms.
- **Material3**: Implements Material Design 3 components for a modern, cohesive look and feel.
- **Compose Resources**: Helps manage resources in a multiplatform environment.
- **FFMPEG-Kit**: Used to apply Lut files to images.
- **Skiko**: Used for rendering images and applying RuntimeShaders.
- **SQLDelight**: Used for local database.
- **Ktor**: Utilized for network operations.
- **Voyager Navigation**: Manages navigation in the app.
- **Koin DI**: Provides dependency injection to manage object creation.
- **Peekaboo Image Picker**: Allows users to pick images from their device.
- **Okio**: Handles I/O operations efficiently.
- **Multiplatform Settings**: Manages user settings consistently across platforms.
- **ImageLoader**: Used for efficient image loading and processing.

## Architecture
The app follows the MVVM (Model-View-ViewModel) architecture and makes use of Kotlin flows for reactive data handling, which helps in managing the UI state reactively across both Android and iOS platforms.

## LUTs Acknowledgment
The Film LUTs used in this app are sourced from a public repository and are not owned by FilmSimulator. You can find them at [YahiaAngelo/Film-Luts](https://github.com/YahiaAngelo/Film-Luts) on GitHub.

## Getting Started

### Prerequisites
- Android Studio Arctic Fox or newer
- Xcode 13 or newer (for iOS)
- Kotlin Multiplatform Mobile Plugin (for Android Studio)

### Building the Project
1. Clone the repository:
   ```bash
   git clone https://github.com/YahiaAngelo/Film-Simulator.git
2. Open the project in Android Studio.
3. To run the Android app, select the 'androidApp' module in the configuration dropdown and hit 'Run'.
4. To run the iOS app, open the iosApp directory in Xcode and run the project on a simulator or actual device.

## Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are greatly appreciated.

1. Fork the Project
2. Create your Feature Branch (git checkout -b feature/AmazingFeature)
3. Commit your Changes (git commit -m 'Add some AmazingFeature')
4. Push to the Branch (git push origin feature/AmazingFeature)
5. Open a Pull Request

## License

Distributed under the MIT License. See `LICENSE` for more information.
