$targetPath = "C:\raks\temp\filesynctest\Raks\Input\20260113_0235\MARA.csv"
$count = 50000

Write-Host "Generating $count records for $targetPath..."

$writer = [System.IO.StreamWriter]::new($targetPath, $true) # Append mode
try {
    for ($i = 1; $i -le $count; $i++) {
        # PRDHA,MAKTX,MATNR,EAN11
        # Data: 01-01-001,Rambler 20oz Tumbler,MAT001,012345678901
        
        $line = "01-01-{0:D5},Test Product {0},MAT{0:D5},400{0:D9}" -f $i
        $writer.WriteLine($line)
        
        if ($i % 5000 -eq 0) {
            Write-Host "Generated $i records..."
        }
    }
}
finally {
    $writer.Close()
}

Write-Host "Done!"
