# VR library for Processing-Android

This library encompasses building the VR scene, objects, render them and pass them to the Papplet and Sketch. It uses GVR to achieve that. A brief about the classes is given below.

## VRActivity:
This Android VRActivity is designed to work with Processing. It provides a simple interface for creating virtual reality (VR) applications in Android. Below are the key features and usage of this VRActivity.

#### Features

- **VR Support:** Designed to work with VR, leveraging the Google VR SDK.
- **Display Metrics:** Access display metrics like width, height, and density.
- **PApplet Integration:** Seamlessly integrate with PApplet, the core class for Processing sketches.
- **Lifecycle Management:** Manage the Android lifecycle events for the sketch.

#### Functions and Use Cases

- **initDimensions():** Initializes the display metrics, enabling access to screen dimensions and density.
- **getDisplayWidth():** Retrieves the width of the display in pixels.
- **getDisplayHeight():** Retrieves the height of the display in pixel.
- **getDisplayDensity():** Retrieves the screen density, which is useful for rendering adjustments.
- **getKind():** Returns the type of VR activity (GVR) associated with the VRActivity.
- **setSketch(PApplet sketch):** Sets the PApplet sketch, enabling the VRActivity to work with a Processing sketch.
- **onResume():** Handles the Android onResume event, allowing the sketch to resume its activity.
- **onPause():** Handles the Android onPause event, pausing the sketch when needed.
- **onDestroy():** Handles the Android onDestroy event, performing cleanup and reasing resources.
- **onStart():** Handles the Android onStart event, starting the sketch as necessary.
- **onStop():** Use Case: Handles the Android onStop event, stopping the sketch when required.
- **onRequestPermissionsResult():** Manages permission request results, ensuring the sketch can request and handle permissions.
- **onNewIntent(Intent intent):** Handles new intent events, useful for responding to incoming data or actions.
- **onActivityResult():** Handles activity result events, allowing the sketch to respond to external interactions.
- **onBackPressed():** Handles the Android back button press, enabling the sketch to respond accordingly.

This VRActivity provides a range of functions for managing the Android lifecycle and integrating with Processing sketches, making it easier to create VR experiences on Android.

## VRCamera:

The `VRCamera` class is designed for managing the camera in a virtual reality (VR) environment using Processing. It offers essential functions to control the camera within a VR scene.

#### Features

- **VR Camera Management:** Designed to work with VR graphics, providing camera control functions.
- **Compatibility Check:** Ensures that the VR renderer is in use before creating a VR camera.
- **Camera Transformation:** Allows camera position and depth range adjustments within a VR scene.

#### Functions and Use Cases

- **VRCamera(PApplet parent):** Constructor for creating a VR camera within a given Processing sketch. Initialize a VR camera to control the viewpoint within a VR scene.
- **sticky():** Sets the camera view to be "sticky," making subsequent transformations relative to the current view. Apply transformations while maintaining the current camera view.
- **noSticky():** Resets the camera view to a non-sticky state, returning to the global coordinate system. Release the sticky camera view and return to the global view.
- **setPosition(float x, float y, float z):** Adjusts the camera position in the VR scene. Set the camera's position in the virtual environment.
- **setNear(float near):** Sets the near clipping plane for the camera. Adjust the near clipping plane to control the rendering range.
- **setFar(float far):** Sets the far clipping plane for the camera. Adjust the far clipping plane to control the rendering range.

This `VRCamera` class provides the necessary functions to control the camera within a VR scene, facilitating interactive and immersive experiences.

## VRGraphics:

The `VRGraphics` class is an extension of `PGraphics3D` designed to handle graphics and camera functionality in a virtual reality (VR) environment using the Processing library. It provides features for rendering and camera control.

#### Features

- **VR Camera:** Supports VR-specific camera transformations and operations.
- **VR Compatibility:** Ensures compatibility with VR graphics for proper rendering.
- **Eye Management:** Provides access to eye-specific rendering parameters.
- **View and Projection:** Manages the view, camera, and projection transformations for the VR scene.

#### Functions and Use Cases

- **beginDraw():** Initializes the rendering process, including updating the camera view for the VR environment.Start the rendering process for a VR scene.
- **camera(float eyeX, float eyeY, float eyeZ, ...):** Disables the ability to set the camera directly in VR. Not applicable in VR; camera settings are managed differently.
- **perspective(float fov, float aspect, float zNear, float zFar):** Disables the ability to set the perspective directly in VR. Not applicable in VR, perspective settings are managed differently.
- The functions `defaultCamera(), defaultPerspective(), saveState() and restoreSurface()` have no functionality as of yet, but can be used for future work.
- **updateView():** Updates the VR view, camera, and projection settings, ensuring proper rendering. Update the view and camera settings for a VR scene.
- **eyeTransform(Eye e):** Sets the current eye view transformation based on the provided `Eye` object. Handle eye-specific transformations and properties.
- **headTransform(HeadTransform ht):** Sets the transformation based on the provided `HeadTransform` object, adjusting camera vectors. Manage the transformation of the head position and orientation.
- **initVR():** Initializes VR-specific settings, vectors, and transformations if not already initialized. Initialize VR-related data structures.
- **setVRViewport():** Sets the viewport for rendering within the VR scene. Adjust the rendering viewport for the current eye.
- **setVRCamera():** Sets the VR camera position and orientation based on the `Eye` object. Manage the camera settings for the VR rendering.
- **setVRProjection():** Sets the projection matrix for rendering within the VR scene. Configure the projection matrix for rendering in the current eye's perspective.

The `VRGraphics` class provides a range of functions to manage rendering and camera transformations within a virtual reality environment. These functions ensure that the VR scene is properly rendered and that camera settings adhere to VR standards.

## VRSurface:

The `VRSurface` class is an integral part of the Processing VR library, designed to facilitate the integration of Processing sketches into a virtual reality (VR) environment. It handles various VR-specific functionalities, surface management, and rendering settings.

#### Features

- **VR Environment:** Optimized for working within a VR environment, utilizing the Google VR SDK.
- **Surface Configuration:** Provides methods to configure and manage the rendering surface within VR.
- **Compatibility:** Ensures compatibility with VR graphics and VR components.
- **Activity Management:** Facilitates interactions with Android's activity and asset management.

#### Functions and Use Cases

- **getContext():** Retrieves the Android `Context` associated with the VR activity. Obtain the Android `Context` for the VR activity.
- **getActivity():** Retrieves the parent activity associated with the VR activity. Access the parent activity of the VR application.
- **finish():** Finishes the VR activity, effectively closing the application. Terminate the VR application.
- **getAssets():** Retrieves the `AssetManager` for the VR activity, providing access to the app's assets. Access assets and resources within the VR application.
- **startActivity(Intent intent):** Starts a new activity based on the provided `Intent`. Launch a new activity from the VR application.
- **initView(int sketchWidth, int sketchHeight):** Initializes the view for the VR activity, making use of the full screen. Configure and set up the VR view for rendering.
- **getName():** Retrieves the package name of the VR application component. Obtain the package name of the VR application component.
- **setOrientation(int which):** Disables the ability to change screen orientation in VR apps. Orientation changes are not allowed in VR apps.
- **getFilesDir():** Retrieves the directory for the app's files. Access the directory for storing app-specific files.
- **openFileInput(String filename):** Provides an `InputStream` for reading a file. Read a file as an `InputStream` for processing.
- **getFileStreamPath(String path):** Retrieves a `File` object for a given file path. Access the `File` object for a specific file.
- **dispose():** No functionality. Reserved for future use or extensions.

Apart from these functions, VRSurface is also responsible for defining the Surface view of the VR app and the StereoRenderer.

- `SurfaceViewVR` is an essential component for rendering virtual reality (VR) content in Processing's VR library on Android devices. It ensures that the device supports OpenGL ES 2.0 and provides essential functionality. This class handles touch and key events, making it interactive, and supports multisampling, which improves the quality of VR graphics. `SurfaceViewVR` serves as the backbone for creating immersive VR experiences in Processing on compatible Android devices.
- The `AndroidVRStereoRenderer` extends GVRView and implements the SteroRenderer. Despite there being two separate classes for defining the mono and stereo renderer, this is handled in VRSurface via this function.

The `VRSurface` class serves as a vital component for managing the rendering surface and other VR-specific settings within the Processing VR library. It provides functions for handling VR activity, configuration, and interaction with Android's activity and assets.
