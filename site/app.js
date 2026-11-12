(() => {
  "use strict";
  const frames = [
    {
      key: "01-dawn",
      name: "黎明",
      clock: "04:00",
      label: "04:00",
      color: "#c9b7e4",
    },
    {
      key: "02-morning",
      name: "早晨",
      clock: "06:00",
      label: "跟随日出，示例回退时间 06:00",
      color: "#f0bf91",
    },
    {
      key: "03-noon",
      name: "正午",
      clock: "11:00",
      label: "11:00",
      color: "#e9cf85",
    },
    {
      key: "04-afternoon",
      name: "午后",
      clock: "14:00",
      label: "14:00",
      color: "#efbd50",
    },
    {
      key: "05-night",
      name: "夜晚",
      clock: "18:00",
      label: "跟随日落，示例回退时间 18:00",
      color: "#d6af9b",
    },
    {
      key: "06-deepnight",
      name: "深夜",
      clock: "23:00",
      label: "23:00",
      color: "#aebce0",
    },
  ];
  const hero = document.querySelector(".hero");
  const comparison = document.querySelector(".comparison");
  const timeline = document.getElementById("experience");
  const timeRange = document.getElementById("time-range");
  const buttons = [...document.querySelectorAll("[data-time]")];
  const slices = [...document.querySelectorAll(".slice")];
  const sources = [...document.querySelectorAll(".preview-source")];
  const images = [...document.querySelectorAll(".preview-image")];
  const clocks = [...document.querySelectorAll(".preview-clock")];
  const announcement = document.getElementById("time-announcement");
  const playButton = document.getElementById("play-toggle");
  const heroPlayButton = document.getElementById("hero-play-toggle");
  const playButtons = [playButton, heroPlayButton];
  const blurRange = document.getElementById("blur-range");
  const blurValue = document.getElementById("blur-value");
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const visibleDemos = new Set([hero]);
  const interval = 2500;
  let selected = 3;
  let timer = null;
  let ready = false;
  let wantsToPlay = !reducedMotion.matches;
  let previewVersion = 0;

  const progress = document.createElement("span");
  progress.className = "timeline-progress";
  progress.setAttribute("aria-hidden", "true");
  const cursor = document.createElement("span");
  cursor.className = "timeline-cursor";
  cursor.setAttribute("aria-hidden", "true");
  timeline.append(progress, cursor);
  timeline.classList.add("has-motion");
  heroPlayButton.hidden = false;

  const previewLayers = [...document.querySelectorAll(".screen-picture")].map(
    (front) => {
      const back = front.cloneNode(true);
      back.classList.add("preview-back");
      back.setAttribute("aria-hidden", "true");
      back
        .querySelectorAll("[id]")
        .forEach((element) => element.removeAttribute("id"));
      back.querySelector("source").classList.remove("preview-source");
      back.querySelector("img").classList.remove("preview-image");
      back.querySelector("img").alt = "";
      front.classList.add("preview-front");
      front.before(back);
      return { front, back };
    },
  );

  function positionCursor() {
    const railY = timeRange.offsetTop + timeRange.offsetHeight / 2;
    const usableWidth = timeRange.clientWidth - 18;
    timeline.style.setProperty(
      "--cursor-x",
      `${(usableWidth * selected) / 5}px`,
    );
    timeline.style.setProperty("--rail-y", `${railY}px`);
    timeline.style.setProperty("--rail-fraction", String(selected / 5));
  }

  async function changePreviews(frame) {
    const version = ++previewVersion;
    previewLayers.forEach(({ front, back }) => {
      back.querySelector("source").srcset =
        front.querySelector("source").srcset;
      back.querySelector("img").src = front.querySelector("img").src;
      back.style.transition = "none";
      front.style.transition = "none";
      back.style.opacity = reducedMotion.matches ? "0" : "1";
      front.style.opacity = reducedMotion.matches ? "1" : "0";
    });
    sources.forEach((source) => {
      source.srcset = `assets/leo/${frame.key}.webp`;
    });
    images.forEach((image) => {
      image.src = `assets/plates/wallpaper-${frame.key.slice(3)}.png`;
      image.alt =
        image.id === "blur-image"
          ? `${frame.name}的桌面模糊效果`
          : `${frame.name}的清晰锁屏效果`;
    });
    await Promise.all(images.map((image) => image.decode?.().catch(() => {})));
    if (version !== previewVersion) return;
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        if (version !== previewVersion) return;
        previewLayers.forEach(({ front, back }) => {
          front.style.transition = "";
          back.style.transition = "";
          front.style.opacity = "1";
          back.style.opacity = "0";
        });
      }),
    );
  }

  function selectFrame(index, userInitiated = false) {
    const next = Math.max(0, Math.min(frames.length - 1, Number(index)));
    const frame = frames[next];
    if (!Number.isInteger(next) || !frame) return;
    const changed = selected !== next;
    selected = next;
    timeRange.value = String(next);
    timeRange.style.setProperty("--progress", `${next * 20}%`);
    timeRange.setAttribute("aria-valuetext", `${frame.name}，${frame.label}`);
    timeline.style.setProperty(
      "--time-duration",
      userInitiated ? "240ms" : "750ms",
    );
    hero.style.setProperty("--active-color", frame.color);
    hero.classList.add("has-interacted");
    buttons.forEach((button, i) =>
      button.setAttribute("aria-pressed", String(i === next)),
    );
    slices.forEach((slice, i) =>
      slice.classList.toggle("is-active", i === next),
    );
    if (changed) changePreviews(frame);
    clocks.forEach((clock) => {
      clock.textContent = frame.clock;
    });
    announcement.setAttribute("aria-live", userInitiated ? "polite" : "off");
    announcement.textContent = `正在预览${frame.name}，${frame.label}。`;
    positionCursor();
  }

  function updatePlaybackControls() {
    const playing = timer !== null;
    playButtons.forEach((button) => {
      button.setAttribute("aria-pressed", String(playing));
      button
        .querySelector("use")
        .setAttribute("href", playing ? "#pause" : "#play");
    });
    playButton.querySelector("span").textContent = playing
      ? "暂停播放"
      : "播放一天";
    const label = playing ? "暂停自动切换" : "播放自动切换";
    heroPlayButton.setAttribute("aria-label", label);
    heroPlayButton.title = label;
    heroPlayButton.querySelector("span").textContent = playing
      ? "暂停"
      : "播放一天";
  }

  function syncPlayback() {
    const shouldPlay =
      ready && wantsToPlay && !document.hidden && visibleDemos.size > 0;
    if (!shouldPlay && timer !== null) {
      clearInterval(timer);
      timer = null;
    } else if (shouldPlay && timer === null) {
      timer = setInterval(
        () => selectFrame((selected + 1) % frames.length),
        interval,
      );
    }
    updatePlaybackControls();
  }

  function stopPlaying() {
    wantsToPlay = false;
    syncPlayback();
  }

  buttons.forEach((button) =>
    button.addEventListener("click", () => {
      stopPlaying();
      selectFrame(Number(button.dataset.time), true);
    }),
  );
  timeRange.addEventListener("input", () => {
    stopPlaying();
    selectFrame(timeRange.value, true);
  });
  timeRange.addEventListener("pointerdown", () => {
    stopPlaying();
    timeline.classList.add("is-scrubbing");
  });
  ["pointerup", "pointercancel"].forEach((event) =>
    window.addEventListener(event, () =>
      timeline.classList.remove("is-scrubbing"),
    ),
  );
  playButtons.forEach((button) =>
    button.addEventListener("click", () => {
      wantsToPlay = !wantsToPlay;
      syncPlayback();
    }),
  );
  [hero, comparison].forEach((demo) =>
    demo.addEventListener("focusin", (event) => {
      if (!playButtons.some((button) => button.contains(event.target)))
        stopPlaying();
    }),
  );
  document.addEventListener("visibilitychange", syncPlayback);
  reducedMotion.addEventListener("change", () => {
    if (reducedMotion.matches) wantsToPlay = false;
    syncPlayback();
  });
  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) =>
          entry.isIntersecting
            ? visibleDemos.add(entry.target)
            : visibleDemos.delete(entry.target),
        );
        syncPlayback();
      },
      { threshold: 0.05 },
    );
    observer.observe(hero);
    observer.observe(comparison);
  }
  if ("ResizeObserver" in window)
    new ResizeObserver(positionCursor).observe(timeline);
  window.addEventListener("resize", positionCursor);
  positionCursor();
  Promise.allSettled(
    [...document.querySelectorAll(".panorama img")].map((image) =>
      image.decode?.(),
    ),
  ).then(() => {
    ready = true;
    syncPlayback();
  });

  function updateBlur() {
    const value = Number(blurRange.value);
    blurValue.textContent = String(value);
    document
      .querySelector(".home-screen")
      .style.setProperty("--blur", `${value}px`);
    blurRange.setAttribute(
      "aria-valuetext",
      value === 0 ? "0，关闭模糊" : `${value}，最大 25`,
    );
  }
  blurRange.addEventListener("input", updateBlur);
  updateBlur();

  document.querySelectorAll(".panorama img").forEach((image) => {
    image.addEventListener("error", () => {
      announcement.textContent = "预览图片暂时未能加载，请刷新重试。";
    });
  });
})();
