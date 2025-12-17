(function(){
    function debounce(func, wait) {
        let timeout;
        return function() {
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(this, arguments), wait);
        };
    }

    // 检查段落是否只包含表情包（没有其他文字内容）
    function isParagraphOnlyStickers(paragraph) {
        const children = Array.from(paragraph.childNodes);
        for (let i = 0; i < children.length; i++) {
            const node = children[i];
            if (node.nodeType === Node.TEXT_NODE) {
                if (node.textContent.trim() !== '') return false;
            } else if (node.nodeType === Node.ELEMENT_NODE) {
                const nodeName = node.nodeName;
                if (nodeName === 'BR') continue;
                if (nodeName === 'IMG' && node.classList.contains('sticker-emoji')) continue;
                return false;
            }
        }
        return true;
    }

    // 检查表情包是否被 BR 标签包围
    function isImageSurroundedByBreaks(img) {
        let prev = img.previousSibling;
        while (prev && prev.nodeType === Node.TEXT_NODE && prev.textContent.trim() === '') {
            prev = prev.previousSibling;
        }
        let next = img.nextSibling;
        while (next && next.nodeType === Node.TEXT_NODE && next.textContent.trim() === '') {
            next = next.nextSibling;
        }
        const prevIsBR = prev && prev.nodeName === 'BR';
        const nextIsBR = next && next.nodeName === 'BR';
        return prevIsBR && nextIsBR;
    }

    // 强制应用内联样式
    function forceApplyStyles(img, isSolo) {
        const root = document.documentElement;
        const style = getComputedStyle(root);
        const inlineMax = style.getPropertyValue('--sticker-inline-max-width').trim() || '64px';
        const soloMax = style.getPropertyValue('--sticker-solo-max-width').trim() || '256px';
        const maxSize = isSolo ? soloMax : inlineMax;
        img.style.setProperty('max-width', maxSize, 'important');
        img.style.setProperty('max-height', maxSize, 'important');
        img.style.setProperty('width', 'auto', 'important');
        img.style.setProperty('height', 'auto', 'important');
        img.style.setProperty('display', 'inline', 'important');
        img.style.setProperty('vertical-align', 'middle', 'important');
    }

    function processParagraph(paragraph) {
        if (!(paragraph instanceof HTMLParagraphElement)) return;
        const stickerImages = paragraph.querySelectorAll('img.sticker-emoji');
        if (stickerImages.length === 0) return;
        
        paragraph.classList.remove('sticker-solo-container');
        stickerImages.forEach(img => img.classList.remove('sticker-solo-image'));
        
        const onlyStickers = isParagraphOnlyStickers(paragraph);
        if (onlyStickers) {
            paragraph.classList.add('sticker-solo-container');
            stickerImages.forEach(img => {
                img.classList.add('sticker-solo-image');
                forceApplyStyles(img, true);
            });
        } else {
            stickerImages.forEach(img => {
                const isSolo = isImageSurroundedByBreaks(img);
                if (isSolo) img.classList.add('sticker-solo-image');
                forceApplyStyles(img, isSolo);
            });
        }
    }

    function processAllStickers() {
        document.querySelectorAll('p').forEach(processParagraph);
        document.querySelectorAll('img.sticker-emoji').forEach(img => {
            if (!img.closest('p')) {
                forceApplyStyles(img, false);
            }
        });
    }

    function init() {
        processAllStickers();
        const debouncedProcess = debounce(processAllStickers, 100);
        const observer = new MutationObserver(mutations => {
            let needsUpdate = false;
            mutations.forEach(mutation => {
                if (mutation.target.nodeName === 'P' || 
                    (mutation.target.closest && mutation.target.closest('p')) || 
                    mutation.addedNodes.length > 0 || 
                    mutation.removedNodes.length > 0) {
                    needsUpdate = true;
                }
            });
            if (needsUpdate) debouncedProcess();
        });
        
        const contentAreas = document.querySelectorAll('.content,.post-content,.article-content,.entry-content,.markdown-body,article,#post-comment,.tk-comments,.comments');
        if (contentAreas.length > 0) {
            contentAreas.forEach(area => observer.observe(area, {childList: true, subtree: true, characterData: true}));
        } else {
            observer.observe(document.body, {childList: true, subtree: true, characterData: true});
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // PJAX 支持
    document.addEventListener('pjax:complete', processAllStickers);
    document.addEventListener('pjax:end', processAllStickers);
    document.addEventListener('swup:contentReplaced', processAllStickers);
    document.addEventListener('turbo:load', processAllStickers);
    document.addEventListener('turbolinks:load', processAllStickers);
    document.addEventListener('visibilitychange', function() {
        if (!document.hidden) processAllStickers();
    });

    // 延迟处理
    setTimeout(processAllStickers, 300);
    setTimeout(processAllStickers, 1000);
})();
