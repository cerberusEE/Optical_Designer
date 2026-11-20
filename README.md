- [x] Fix the viewer, the cones are not drawn correctly. Also fix lens position and cone interaction.
- [x] If there is a detector screen, do not propagate the light beyond it, the detector screen is the end for the light. The optical efficiency will then be the ratio of total power that ends up on the screen/aperture.
- [x] If there is an detector screen, assume it is circular and integrate the irradiance over the screen. Show the integrated power in the analysis tab (if there is a detector screen. do not show that field if there is none).

- [x] remove all sliders, manual entry for all parameters. use standard edit boxes. In particular support detector screen at kilometer ranges.

- [x] Add an irradiance at range value for analysis so that one can compare the results of a shorter focal length lens with better efficiency vs a longer focal length lens with poorer efficiency.
- [x] As a follow on, add source radiant intensity at range value. units W/sr.


TODO: Add memory, the application shall remember the last used project. Also add a way to save and share projects. Use json format for saving to file or share using share intent.

- [x] Draw light cone with the correct color if applicable. Use light gray for colors outside visible light. The application shall support all optical wavelengths from vacuum UV to far LWIR.

- [x] Add support for more lens materials, in particular Quartz, germanium, silicon and ZnSe. Get lens data from thorlabs or edmund optics.

- [x] Add support for coated optics, manual entry for transmission. supply a checkbox that opens an editbox for the transmission.

- [x] Remove center thickness input. Calculate from other parameters, assuming spherical lens.

TODO: Support viewer navigation using gestures. Allow for a tighter zoom for more details.

- [x] Add support for dark theme and app shall follow current phone theme. Dark theme can have a black background and light theme can use a bright background. Shade the light cones darker for light mode and lighter for dark mode to improve contrast. The text and horizontal axis shall be maximally contrasted against the background.

TODO: Explain the consequences of the calculated spherical aberration. Also implement any such effects in the divergence calculations.
