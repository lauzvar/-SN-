'use strict';
async function loadImportRobots(){
  if(!isTech())throw Error('仅管理员和技术人员可以导入');
  state.options=await api('/options');
  show(heading('Excel 新建机器人','下载空白模板，填写后校验预览，再确认整批新建。',action('nav','返回台账',attr('page','robots'))+'<a class="button" href="/api/import/robots/template">下载 Excel 空白模板</a>')+
    panel('1 · 选择文件',`<form id="excel-upload" class="panel-body"><p class="notice">保留原表 46 列和第 3 行表头，从第 4 行填写。仅支持 .xlsx，最多 2 MB、100 台。空白版本保持为空。</p><div class="form-grid"><label class="wide"><span>Excel 模板文件 *</span><input id="excel-file" type="file" name="file" accept=".xlsx" required></label><label><span>空 SN 的生产年月 YYMM</span><input name="month" value="${new Date().toLocaleDateString('sv-SE').slice(2,7).replace('-','')}" pattern="[0-9]{4}" maxlength="4"></label><label><span>空 SN 的生产类型</span><select name="type">${state.options.rule.types.map(t=>`<option ${t==='P'?'selected':''}>${h(t)}</option>`).join('')}</select></label></div><p class="help">新建状态只能为在库。交付日期、整机测试及质检字段留空，建档后通过流程登记。客户名称不建立账号绑定。没有 SN 生成授权时，请填写已预留 SN；有业务范围限制时只能填写范围内的编号。</p><button class="primary" type="submit">校验并预览</button></form>`)+
    '<div id="excel-error" class="error" role="alert" tabindex="-1" hidden></div><div id="excel-preview" aria-live="polite"></div>');
  const form=$('#excel-upload'),output=$('#excel-preview'),error=$('#excel-error');
  let token='';
  form.addEventListener('input',()=>{token='';output.innerHTML='';error.hidden=true;});
  form.addEventListener('submit',async event=>{
    event.preventDefault();const button=form.querySelector('button');button.disabled=true;button.textContent='正在校验…';error.hidden=true;output.innerHTML='';token='';
    try{
      const file=$('#excel-file').files[0];if(!file||file.size>2*1024*1024)throw Error('请选择不超过 2 MB 的 .xlsx 文件');
      const headers={};headers[$('meta[name="csrf-header"]').content]=$('meta[name="csrf-token"]').content;
      const res=await fetch('/api/import/robots/preview',{method:'POST',headers,body:new FormData(form)});
      if(res.status===401){location.href='/login';return;}
      const data=await res.json();if(!res.ok)throw Error(data.message||'预览失败');
      token=data.token;
      const rowErrors=new Map(data.errors.map(e=>[e.row,e.message]));
      output.innerHTML=panel('2 · 核对导入内容',`<div class="panel-body"><p>${h(data.message)}</p><p class="help">${h(data.filename)} · ${data.rows.length} 台 · ${data.valid?'校验通过，预览 10 分钟内有效':'存在错误，请修改文件后重新预览'}</p></div>`+
        table(['Excel 行','SN','待写入内容','校验结果'],data.rows.map(r=>[h(r.row),h(r.sn||'确认时自动分配'),`<details><summary>${h(r.fields.model||'未填写型号')} · 查看全部填写项</summary>${table(['字段','值'],Object.entries(r.fields).map(([k,v])=>[h(state.fields.find(f=>f.key===k)?.label||k),h(v)]).concat(Object.entries(r.modules).map(([k,v])=>[h(k+' SN'),h(v)])))}</details>`,rowErrors.has(r.row)?`<span class="error">${h(rowErrors.get(r.row))}</span>`:'可新建']))+
        (data.valid?'<form id="excel-confirm" class="panel-body"><label><span>导入原因 *</span><textarea name="reason" required maxlength="4000" placeholder="说明本批机器人建档原因"></textarea></label><p class="help">只新建，不覆盖已存在的机器人。确认后保存操作者、来源坐标、映射和导入记录。</p><button class="primary" type="submit">确认新建 '+data.rows.length+' 台机器人</button></form>':''));
      if(!data.valid){error.textContent='第 '+data.errors[0].row+' 行：'+data.errors[0].message;error.hidden=false;error.focus();}
      $('#excel-confirm')?.addEventListener('submit',async e=>{
        e.preventDefault();const submit=e.target.querySelector('button');submit.disabled=true;submit.textContent='正在提交…';error.hidden=true;
        try{
          if(!token)throw Error('请重新校验预览');
          const result=await api('/import/robots/confirm','POST',{token,reason:new FormData(e.target).get('reason')});token='';
          output.innerHTML=panel('导入完成',`<div class="panel-body"><p role="status">成功新建 ${result.count} 台机器人。已记录导入批次与操作历史。</p></div>`+table(['Excel 行','新机器人 SN','操作'],result.robots.map(r=>[h(r.row),h(r.sn),action('detail','查看档案',attr('sn',r.sn))])));
          form.reset();toast('整批导入成功');
        }catch(ex){token='';error.textContent=ex.message+'。请重新预览后提交。';error.hidden=false;error.focus();submit.textContent='请重新校验预览';}
      });
    }catch(ex){error.textContent=ex.message;error.hidden=false;error.focus();}
    finally{button.disabled=false;button.textContent='校验并预览';}
  });
}
