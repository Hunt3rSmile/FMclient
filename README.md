<div align="center">

# FMclient

**Современный лаунчер Minecraft**

![Version](https://img.shields.io/badge/version-1.2.5-a855f7?style=for-the-badge)
![Electron](https://img.shields.io/badge/Electron-33-47848F?style=for-the-badge&logo=electron&logoColor=white)
![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20Linux-0d0d0d?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-22c55e?style=for-the-badge)

[Скачать](https://github.com/Hunt3rSmile/FMclient/releases/latest) • [Релизы](https://github.com/Hunt3rSmile/FMclient/releases)

</div>

---

## Возможности

- **Аккаунты** — Microsoft (MSA), Ely.by, офлайн
- **Загрузчики** — Vanilla, Fabric, Forge, NeoForge
- **FMclient Visuals** — встроенный мод с кастомным интерфейсом и визуальными настройками
- **Автоустановка Java** — проверка совместимости перед запуском
- **Мультиплатформенность** — Windows и Linux (AppImage, .deb, .pacman)

## FMclient Visuals

Встроенный Fabric-мод, который автоматически устанавливается перед каждым запуском.

- Кастомный главный экран Minecraft в фирменном стиле (тёмный фон, фиолетовые кнопки)
- Нажми **LSHIFT** в игре для быстрого доступа к настройкам:
  - Фулбрайт
  - Тени мобов
  - Режим графики
  - Частицы
  - Полный экран

## Установка

| Платформа | Файл |
|-----------|------|
| Windows | `FMclient Setup x.x.x.exe` |
| Linux (AppImage) | `FMclient-x.x.x.AppImage` |
| Debian / Ubuntu | `fmclient_x.x.x_amd64.deb` |
| Arch / Manjaro | `fmclient-x.x.x.pacman` |

Скачай нужный файл со страницы [Releases](https://github.com/Hunt3rSmile/FMclient/releases/latest).

## Разработка

```bash
npm install
npm start
```

Сборка:
```bash
npm run build:all      # Linux + Windows
npm run build:pacman   # Arch/Manjaro .pacman
```

## Стек

- [Electron](https://www.electronjs.org/) — оболочка
- [minecraft-launcher-core](https://github.com/Pierce01/MinecraftLauncher-core) — запуск игры
- [Fabric Loom](https://github.com/FabricMC/fabric-loom) — сборка мода fmvisuals
