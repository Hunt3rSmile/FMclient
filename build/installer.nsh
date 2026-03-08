; FMclient — Custom NSIS Installer Script
; Uses !ifndef guards to avoid redefining macros set by electron-builder

!macro customHeader
  !ifndef MUI_HEADER_BITMAP
    !define MUI_HEADER_BITMAP "${BUILD_RESOURCES_DIR}\installer-header.bmp"
  !endif
  !ifndef MUI_HEADER_BITMAP_RIGHT
    !define MUI_HEADER_BITMAP_RIGHT
  !endif
  !ifndef MUI_WELCOMEPAGE_TITLE
    !define MUI_WELCOMEPAGE_TITLE "Добро пожаловать в FMclient"
  !endif
  !ifndef MUI_WELCOMEPAGE_TEXT
    !define MUI_WELCOMEPAGE_TEXT "Установщик установит FMclient — современный лаунчер для Minecraft.$\r$\n$\r$\nНажмите Далее для продолжения."
  !endif
  !ifndef MUI_FINISHPAGE_TITLE
    !define MUI_FINISHPAGE_TITLE "FMclient установлен!"
  !endif
  !ifndef MUI_FINISHPAGE_TEXT
    !define MUI_FINISHPAGE_TEXT "FMclient успешно установлен.$\r$\n$\r$\nЗапустите лаунчер и начните играть!"
  !endif
  !ifndef MUI_FINISHPAGE_RUN
    !define MUI_FINISHPAGE_RUN "$INSTDIR\FMclient.exe"
  !endif
  !ifndef MUI_FINISHPAGE_RUN_TEXT
    !define MUI_FINISHPAGE_RUN_TEXT "Запустить FMclient"
  !endif
!macroend
