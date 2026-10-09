# GlassEmu skins and appearance

Open Settings → General → Skins to import a Nintendo DS `.deltaskin` archive or select default controls, the included DSi skin, or an imported skin. Imports are copied into private app storage and selected automatically. The original archive can be moved or deleted afterwards.

The importer accepts two-screen Nintendo DS layouts with PDF or PNG artwork. It reads available portrait and landscape representations, uses the closest mapping aspect ratio, and fits artwork and input frames together without stretching the screen layout. The included skin pairs the original white DSi portrait with a flat white landscape adaptation of the supplied Candybar layout by Dwichotomy. Imported copies of the original DSi portrait also use this landscape fallback. Other skins with a missing orientation use the existing emulator controls. Skin changes apply when returning from settings or launching a game.

Circle pads retain their captured pointer until release/cancellation, even outside the original touch radius; the cap remains clamped to its visual base. Circle pads send digital D-pad directions (DS games do not receive analog input). Directional ABXY clusters keep their own button mappings. Landscape supports both the fast-forward toggle and holding the main screen to fast-forward.

D-pad diagonals, simultaneous button presses, touchscreen input, Menu, quick save/load, and common emulator action buttons use the existing melonDS input handlers. Touch cancellation releases held inputs. Controller visibility and haptic preferences remain active.

Archives for other consoles, missing artwork, invalid frames, cropped/reordered screen inputs, and screen effects are rejected with an import error. Import limits bound archive and artwork sizes; referenced assets are saved under generated names rather than archive paths.

Open Settings → General → Theme for Light, Dark, or System default. The library, navigation, settings groups, preference rows, and dialogs share the purple day/night palette.
