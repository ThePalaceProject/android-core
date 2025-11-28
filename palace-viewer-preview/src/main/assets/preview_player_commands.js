PalacePreviewPlayerCommands = {
  pausePlayer: () => {
    const button = document.querySelector('button[aria-label="Pause"]');
    if (button) {
      const event = document.createEvent('HTMLEvents');

      event.initEvent('click', true, true);
      button.dispatchEvent(event);
    }
  }
};