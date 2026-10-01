@echo off
rem Deletes the files removed in MlumInventory 3.5.0. Put this next to gradlew.bat and run it.
cd /d "%~dp0src\main\java\com\barbwra\mlum"
del /q client\gui\BagDrag.java client\gui\BagDraw.java client\gui\BagRender.java 2>nul
del /q client\gui\FactionScreen.java client\gui\FactionTab.java client\gui\FactionVaultScreen.java 2>nul
del /q client\gui\LevelScreen.java client\gui\MlumScreen.java client\gui\NavBar.java 2>nul
del /q client\gui\NavRoute.java client\gui\NavTab.java client\gui\QuestScreen.java 2>nul
del /q client\gui\ScreenSwitch.java client\gui\SlotGhost.java client\gui\TextField.java 2>nul
del /q client\gui\Tooltip.java client\gui\VehicleScreen.java menu\FactionVaultMenu.java 2>nul
echo Done - old files removed. Now run: gradlew build
pause
