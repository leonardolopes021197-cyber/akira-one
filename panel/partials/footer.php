  </main>
</div>
<script>
for (const el of document.querySelectorAll('[data-confirm]')) {
  el.addEventListener('click', e => { if (!confirm(el.dataset.confirm)) e.preventDefault(); });
}
</script>
</body>
</html>
