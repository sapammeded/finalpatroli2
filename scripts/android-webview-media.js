// Android-only media bridge for the STT APK. The original HTML photo workflow stays intact.
(function(){
  function dataUrlToFile(dataUrl, name){
    var parts=dataUrl.split(',');
    var mime=(parts[0].match(/data:([^;]+)/)||[])[1]||'image/jpeg';
    var bin=atob(parts[1]||'');
    var bytes=new Uint8Array(bin.length);
    for(var i=0;i<bin.length;i++) bytes[i]=bin.charCodeAt(i);
    return new File([bytes],name,{type:mime});
  }
  function injectFile(inputId,dataUrl){
    var input=document.getElementById(inputId);
    if(!input||!dataUrl) return;
    try{
      var dt=new DataTransfer();
      dt.items.add(dataUrlToFile(dataUrl,'STT_'+Date.now()+'.jpg'));
      input.files=dt.files;
      input.dispatchEvent(new Event('change',{bubbles:true}));
    }catch(e){console.error('STT native media bridge',e);}
  }
  function bind(){
    var cam=document.getElementById('openNativeCamera');
    var third=document.getElementById('openThirdPartyCamera');
    if(cam && !cam.dataset.nativeBound){
      cam.dataset.nativeBound='1';
      cam.addEventListener('click',function(e){
        e.preventDefault(); e.stopImmediatePropagation();
        if(window.AndroidMedia) window.AndroidMedia.captureCamera('native');
      },true);
    }
    if(third && !third.dataset.nativeBound){
      third.dataset.nativeBound='1';
      third.addEventListener('click',function(e){
        e.preventDefault(); e.stopImmediatePropagation();
        if(window.AndroidMedia) window.AndroidMedia.captureCamera('thirdparty');
      },true);
    }
  }
  window.addEventListener('sttNativeCameraResult',function(e){
    var d=e.detail&&e.detail.dataUrl;
    if(!d) return;
    var target=document.getElementById('targetAreaSelect');
    var inputId=(target&&target.dataset&&target.dataset.nativeSource==='thirdparty')?'thirdPartyCameraInput':'nativeCameraInput';
    // Prefer the button/source used most recently; default to native camera input.
    if(window.__STT_LAST_MEDIA_SOURCE==='thirdparty') inputId='thirdPartyCameraInput';
    injectFile(inputId,d);
  });
  document.addEventListener('click',function(e){
    var el=e.target&&e.target.closest?e.target.closest('#openThirdPartyCamera'):null;
    if(el) window.__STT_LAST_MEDIA_SOURCE='thirdparty';
    var el2=e.target&&e.target.closest?e.target.closest('#openNativeCamera'):null;
    if(el2) window.__STT_LAST_MEDIA_SOURCE='native';
  },true);
  window.addEventListener('sttNativeCameraError',function(e){alert((e.detail&&e.detail.message)||'Kamera gagal dibuka');});
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',bind); else bind();
})();
