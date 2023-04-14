function pausePlayer() {
    const button = document.getElementsByClassName('playback-toggle halo')[0];
    const event = document.createEvent('HTMLEvents');

    event.initEvent('click', true, true);
    button.dispatchEvent(event);
}