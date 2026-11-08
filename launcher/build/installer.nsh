; Lumina's privacy page in the installer (included by electron-builder from build/installer.nsh).
; It says what Lumina sends where and lets the user turn Lumina Online off before installing.
; Unticked, the installer leaves launcher\install-options.json; the launcher moves that choice into
; Lumina's settings on its first start (profiles.applyInstallOptions). Silent updates skip the page
; and change nothing.

!ifndef BUILD_UNINSTALLER

!include nsDialogs.nsh
!include LogicLib.nsh

Var LuminaOnlineBox
Var LuminaOnlineState

!macro customPageAfterChangeDir
  Page custom LuminaPrivacyShow LuminaPrivacyLeave
!macroend

Function LuminaPrivacyShow
  ; the page heading (what MUI_HEADER_TEXT does; MUI is included after this file)
  GetDlgItem $1 $HWNDPARENT 1037
  SendMessage $1 ${WM_SETTEXT} 0 "STR:Privacy"
  GetDlgItem $1 $HWNDPARENT 1038
  SendMessage $1 ${WM_SETTEXT} 0 "STR:What Lumina sends, and where"

  nsDialogs::Create 1018
  Pop $0
  ${If} $0 == error
    Abort
  ${EndIf}

  ${NSD_CreateLabel} 0 0 100% 44u "Lumina Online shows your cape, particle effect and the Lumina mark to other Lumina players, and theirs to you. For this the game sends your Minecraft name, UUID and what you wear to Lumina's server. It stores no IP addresses and keeps no logs."
  Pop $0
  ${NSD_CreateLabel} 0 48u 100% 30u "The launcher also uses Microsoft and Mojang (sign-in, game files), Modrinth (mods) and GitHub (updates). Your Microsoft sign-in never reaches Lumina's server."
  Pop $0
  ${NSD_CreateLink} 0 82u 100% 12u "Read the full privacy policy"
  Pop $0
  ${NSD_OnClick} $0 LuminaOpenPrivacy

  ${NSD_CreateCheckbox} 0 104u 100% 12u "Turn on Lumina Online (you can change this later in the Lumina menu)"
  Pop $LuminaOnlineBox
  ${If} $LuminaOnlineState == ${BST_UNCHECKED}
    ${NSD_Uncheck} $LuminaOnlineBox
  ${Else}
    ${NSD_Check} $LuminaOnlineBox
  ${EndIf}

  nsDialogs::Show
FunctionEnd

Function LuminaOpenPrivacy
  Pop $0
  ExecShell "open" "https://github.com/johan13p1/lumina-releases/blob/main/PRIVACY.md"
FunctionEnd

Function LuminaPrivacyLeave
  ${NSD_GetState} $LuminaOnlineBox $LuminaOnlineState
FunctionEnd

!macro customInstall
  ${If} $LuminaOnlineState == ${BST_UNCHECKED}
    CreateDirectory "$APPDATA\.luminaclient\launcher"
    FileOpen $0 "$APPDATA\.luminaclient\launcher\install-options.json" w
    FileWrite $0 '{"online":false}'
    FileClose $0
  ${EndIf}
!macroend

!endif
